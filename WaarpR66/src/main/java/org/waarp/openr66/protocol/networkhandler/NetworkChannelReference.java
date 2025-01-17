/*
 * This file is part of Waarp Project (named also Waarp or GG).
 *
 *  Copyright (c) 2019, Waarp SAS, and individual contributors by the @author
 *  tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 *  All Waarp Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 * Waarp . If not, see <http://www.gnu.org/licenses/>.
 */
package org.waarp.openr66.protocol.networkhandler;

import io.netty.channel.Channel;
import org.waarp.common.future.WaarpLock;
import org.waarp.common.logging.SysErrLogger;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.lru.ConcurrentUtility;
import org.waarp.openr66.context.ErrorCode;
import org.waarp.openr66.context.R66Result;
import org.waarp.openr66.context.R66Session;
import org.waarp.openr66.context.task.exception.OpenR66RunnerErrorException;
import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.database.data.DbTaskRunner.TASKSTEP;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolRemoteShutdownException;
import org.waarp.openr66.protocol.exception.OpenR66ProtocolSystemException;
import org.waarp.openr66.protocol.localhandler.LocalChannelReference;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.waarp.openr66.protocol.configuration.Configuration.*;

/**
 * NetworkChannelReference object to keep Network channel open while some local
 * channels are attached to it.
 */
public class NetworkChannelReference {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(NetworkChannelReference.class);

  private static final LocalChannelReference[] LCR_0_LENGTH =
      new LocalChannelReference[0];

  /**
   * Does this Network Channel is in shutdown
   */
  protected boolean isShuttingDown;
  /**
   * Associated LocalChannelReference
   */
  private final Set<LocalChannelReference> localChannelReferences =
      ConcurrentUtility.newConcurrentSet();
  /**
   * Network Channel
   */
  protected final Channel channel;
  /**
   * Remote network address (when valid)
   */
  protected final SocketAddress networkAddress;
  /**
   * Remote IP address
   */
  private final String hostAddress;
  /**
   * Remote Host Id
   */
  private String hostId;
  /**
   * ClientNetworkChannels object that contains this NetworkChannelReference
   */
  protected ClientNetworkChannels clientNetworkChannels;
  /**
   * Associated lock
   */
  protected final WaarpLock lock;
  /**
   * Last Time in ms this channel was used by a LocalChannel
   */
  private long lastTimeUsed = System.currentTimeMillis();
  /**
   * Is this channel multiplexed using Ssl
   */
  private final boolean isSSL;

  public NetworkChannelReference(final Channel networkChannel,
                                 final WaarpLock lock, final boolean isSSL) {
    channel = networkChannel;
    networkAddress = channel.remoteAddress();
    hostAddress =
        ((InetSocketAddress) networkAddress).getAddress().getHostAddress();
    this.lock = lock;
    this.isSSL = isSSL;
  }

  public NetworkChannelReference(final SocketAddress address,
                                 final WaarpLock lock, final boolean isSSL) {
    channel = null;
    networkAddress = address;
    hostAddress =
        ((InetSocketAddress) networkAddress).getAddress().getHostAddress();
    this.lock = lock;
    this.isSSL = isSSL;
  }

  public final boolean isSSL() {
    return isSSL;
  }

  public final void add(final LocalChannelReference localChannel)
      throws OpenR66ProtocolRemoteShutdownException {
    // lock is of no use since caller is itself in locked situation for the very same lock
    if (isShuttingDown) {
      throw new OpenR66ProtocolRemoteShutdownException(
          "Current NetworkChannelReference is closed");
    }
    use();
    localChannelReferences.add(localChannel);
  }

  /**
   * To set the last time used
   */
  public final void use() {
    if (!isShuttingDown) {
      lastTimeUsed = System.currentTimeMillis();
    }
  }

  /**
   * To set the last time used when correct
   *
   * @return True if last time used is set
   */
  public final boolean useIfUsed() {
    if (!isShuttingDown && !localChannelReferences.isEmpty()) {
      lastTimeUsed = System.currentTimeMillis();
      return true;
    }
    return false;
  }

  /**
   * Remove one LocalChanelReference, closing it if necessary.
   *
   * @param localChannel
   */
  public final void closeAndRemove(final LocalChannelReference localChannel) {
    if (!localChannel.getFutureRequest().isDone()) {
      localChannel.close();
    }
    remove(localChannel);
  }

  /**
   * Remove one LocalChanelReference
   *
   * @param localChannel
   */
  public final void remove(final LocalChannelReference localChannel) {
    localChannelReferences.remove(localChannel);
    // Do not since it prevents shutdown: lastTimeUsed = System.currentTimeMillis()
  }

  /**
   * Shutdown All Local Channels associated with this NCR
   */
  public final void shutdownAllLocalChannels() {
    lock.lock(Configuration.WAITFORNETOP, TimeUnit.MILLISECONDS);
    try {
      logger.info("Will shutdown all local channels");
      isShuttingDown = true;
      final LocalChannelReference[] localChannelReferenceArray =
          localChannelReferences.toArray(LCR_0_LENGTH);
      final ArrayList<LocalChannelReference> toCloseLater =
          new ArrayList<LocalChannelReference>();
      for (final LocalChannelReference localChannelReference : localChannelReferenceArray) {
        localChannelReference.getFutureRequest().awaitOrInterruptible(
            Configuration.configuration.getTimeoutCon() / 3);
        if (!localChannelReference.getFutureRequest().isDone()) {
          localChannelReference.getFutureValidRequest().awaitOrInterruptible(
              Configuration.configuration.getTimeoutCon() / 3);
          if (localChannelReference.getFutureValidRequest().isDone() &&
              localChannelReference.getFutureValidRequest().isFailed()) {
            toCloseLater.add(localChannelReference);
            continue;
          } else {
            final R66Result finalValue =
                new R66Result(localChannelReference.getSession(), true,
                              ErrorCode.Shutdown, null);
            if (localChannelReference.getSession() != null) {
              try {
                localChannelReference.getSession()
                                     .tryFinalizeRequest(finalValue);
              } catch (final OpenR66RunnerErrorException ignored) {
                // nothing
              } catch (final OpenR66ProtocolSystemException ignored) {
                // nothing
              }
            }
          }
        }
        localChannelReference.close();
      }
      try {
        Thread.sleep(Configuration.WAITFORNETOP);
      } catch (final InterruptedException e) {//NOSONAR
        SysErrLogger.FAKE_LOGGER.ignoreLog(e);
      }
      for (final LocalChannelReference localChannelReference : toCloseLater) {
        localChannelReference.getFutureRequest().awaitOrInterruptible(
            Configuration.configuration.getTimeoutCon() / 3);
        localChannelReference.close();
      }
      toCloseLater.clear();
    } finally {
      lock.unlock();
    }
  }

  /**
   * @param localChannelReference the localChannelReference to be closed
   *
   * @return True if the localChannelReference is the only one still active or there is no more LCR
   */
  public final boolean isLastLocalChannelActive(
      final LocalChannelReference localChannelReference) {
    final boolean someActive = isSomeLocalChannelsActive();
    return
        (someActive && localChannelReferences.contains(localChannelReference) &&
         localChannelReferences.size() == 1) ||
        localChannelReferences.isEmpty();
  }

  /**
   * @return -1 if not allowed, 0 if allowed, else time in ms before ready to recheck
   */
  public final long shutdownAllowed() {
    lock.lock(Configuration.WAITFORNETOP, TimeUnit.MILLISECONDS);
    try {
      logger.debug("NC count: {}", this);
      if (nbLocalChannels() <= 0) {
        boolean reallyShutdownNetwork = true;
        for (int i = 0; i < RETRYNB; i++) {
          try {
            Thread.sleep(RETRYINMS);
          } catch (final InterruptedException e) { // NOSONAR
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
          }
          if (nbLocalChannels() != 0) {
            reallyShutdownNetwork = false;
            break;
          }
        }
        if (reallyShutdownNetwork) {
          long time =
              checkLastTime(Configuration.configuration.getTimeoutCon() * 2);
          if (time > Configuration.RETRYINMS &&
              Configuration.configuration.isTimerCloseReady()) {
            logger.debug("NC reschedule at {} : {}", time, this);
            // will re execute this request later on
            time = (time / 10) * 10 + 100; // round to 10
            return time;
          }
          logger.info("Closing NETWORK channel {}", this);
          return 0;
        } else {
          use();
          logger.debug("Ignore closing Network channel");
          return -1;
        }
      }
      return -1;
    } finally {
      lock.unlock();
    }
  }

  public final void lockNetwork() {
    lock.lock(Configuration.WAITFORNETOP, TimeUnit.MILLISECONDS);
  }

  public final void unlockNetwork() {
    lock.unlock();
  }

  public final int nbLocalChannels() {
    return localChannelReferences.size();
  }

  /**
   * @return True if at least one LocalChannel is not yet finished (OK or Error)
   */
  public final boolean isSomeLocalChannelsActive() {
    lock.lock(Configuration.WAITFORNETOP, TimeUnit.MILLISECONDS);
    try {
      for (final LocalChannelReference localChannelReference : localChannelReferences) {
        final R66Session session = localChannelReference.getSession();
        if (session != null) {
          final DbTaskRunner runner = session.getRunner();
          if (runner != null && !runner.isFinished() &&
              runner.getGlobalStep() != TASKSTEP.NOTASK) {
            return true;
          }
        }
      }
      return false;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public final String toString() {
    return "NC: " + hostId + ':' + (channel != null && channel.isActive()) +
           ' ' + networkAddress + " Count: " + localChannelReferences.size();
  }

  @Override
  public final boolean equals(final Object obj) {
    if (obj instanceof NetworkChannelReference) {
      final NetworkChannelReference obj2 = (NetworkChannelReference) obj;
      if (obj2.channel == null || channel == null) {
        return false;
      }
      return obj2.channel.id().compareTo(channel.id()) == 0;
    }
    return false;
  }

  @Override
  public final int hashCode() {
    if (channel == null) {
      return Integer.MIN_VALUE;
    }
    return channel.id().hashCode();
  }

  /**
   * @return the hashcode for the global remote networkaddress
   */
  public final int getSocketHashCode() {
    return networkAddress.hashCode();
  }

  /**
   * Used for BlackList
   *
   * @return the hashcode for the address
   */
  public final int getAddressHashCode() {
    return hostAddress.hashCode();
  }

  /**
   * Check if the last time used is ok with a delay applied to the current
   * time
   * (timeout)
   *
   * @param delay
   *
   * @return <= 0 if OK, else > 0 (should send a KeepAlive or wait that time
   *     in ms)
   */
  public final long checkLastTime(final long delay) {
    return lastTimeUsed + delay - System.currentTimeMillis();
  }

  /**
   * @return the isShuttingDown
   */
  public final boolean isShuttingDown() {
    return isShuttingDown;
  }

  /**
   * @return the channel
   */
  public final Channel channel() {
    return channel;
  }

  /**
   * @return the hostId
   */
  public final String getHostId() {
    return hostId;
  }

  /**
   * @param hostId the hostId to set
   */
  public final void setHostId(final String hostId) {
    this.hostId = hostId;
  }

  /**
   * @return the lastTimeUsed
   */
  public final long getLastTimeUsed() {
    return lastTimeUsed;
  }

}
