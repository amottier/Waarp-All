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
package org.waarp.ftp.core.data;

import org.waarp.common.command.ReplyCode;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.ftp.core.command.FtpCommandCode;
import org.waarp.ftp.core.exception.FtpNoConnectionException;
import org.waarp.ftp.core.exception.FtpNoFileException;
import org.waarp.ftp.core.session.FtpSession;

import java.util.List;

/**
 * Class that implements the execution of the Transfer itself.
 */
class FtpTransferExecutor implements Runnable {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(FtpTransferExecutor.class);

  /**
   * Ftp SessionInterface
   */
  private final FtpSession session;

  /**
   * FtpTransfer
   */
  private final FtpTransfer executeTransfer;

  /**
   * Create an executor and launch it
   *
   * @param session
   * @param executeTransfer
   */
  FtpTransferExecutor(final FtpSession session,
                      final FtpTransfer executeTransfer) {
    this.session = session;
    this.executeTransfer = executeTransfer;
    if (this.executeTransfer == null) {
      this.session.getDataConn().getFtpTransferControl().setEndOfTransfer();
      logger.error("No Execution to do");
    }
  }

  /**
   * Internal method, should not be called directly
   */
  @Override
  public void run() {
    if (executeTransfer == null) {
      session.getDataConn().getFtpTransferControl().setEndOfTransfer();
      logger.error("No Execution to do");
      return;
    }
    try {
      runNextCommand();
    } catch (final InterruptedException e) {//NOSONAR
      logger.error("Executor Interrupted: {}", e.getMessage(), e);
    }
  }

  /**
   * Run the next command or wait for the next
   *
   * @throws InterruptedException
   */
  private void runNextCommand() throws InterruptedException {
    if (FtpCommandCode.isStoreLikeCommand(executeTransfer.getCommand())) {
      // The command is implicitly done by receiving message
      waitForCommand();
      // Store set end
      try {
        session.getDataConn().getFtpTransferControl().setEndOfTransfer();
      } catch (final NullPointerException e) {
        // ignore, due probably to an already clean session
      }
    } else if (FtpCommandCode.isListLikeCommand(executeTransfer.getCommand())) {
      // No wait for Command since the answer is already there
      final List<String> list = executeTransfer.getInfo();
      final StringBuilder builder = new StringBuilder();
      for (final String newfileInfo : list) {
        builder.append(newfileInfo).append(ReplyCode.CRLF);
      }
      if (builder.length() == 0) {
        builder.append(ReplyCode.CRLF);
      }
      final String message = builder.toString();
      boolean status = false;
      try {
        status =
            session.getDataConn().getDataNetworkHandler().writeMessage(message);
      } catch (final FtpNoConnectionException e) {
        logger.error("No Connection but should not be!", e);
      }
      // Set status for check, no wait for the command
      executeTransfer.setStatus(status);
      // must explicitly set the end and no wait
      session.getDataConn().getFtpTransferControl().setEndOfTransfer();
    } else if (FtpCommandCode.isRetrLikeCommand(executeTransfer.getCommand())) {
      // The command must be launched
      try {
        executeTransfer.getFtpFile().trueRetrieve();
      } catch (final FtpNoFileException e) {
        // an error occurs
        logger.error(e);
        session.getDataConn().getFtpTransferControl().setEndOfTransfer();
      }
      logger.debug("wait for end of command");
      waitForCommand();
      logger.debug("RETR ending");
      // RETR set end
      try {
        session.getDataConn().getFtpTransferControl().setEndOfTransfer();
      } catch (final NullPointerException e) {
        // ignore, due probably to an already clean session
      }
    } else {
      // This is an error as unknown transfer command
      session.getDataConn().getFtpTransferControl().setEndOfTransfer();
    }
  }

  /**
   * Wait for the command to finish
   *
   * @throws InterruptedException
   */
  private void waitForCommand() throws InterruptedException {
    session.getDataConn().getFtpTransferControl().waitForEndOfTransfer();
  }
}
