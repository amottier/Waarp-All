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
package org.waarp.ftp.simpleimpl.file;

import org.waarp.common.command.NextCommandReply;
import org.waarp.common.command.ReplyCode;
import org.waarp.common.command.exception.Reply421Exception;
import org.waarp.common.command.exception.Reply502Exception;
import org.waarp.common.command.exception.Reply530Exception;
import org.waarp.common.file.DirInterface;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.ftp.core.command.FtpCommandCode;
import org.waarp.ftp.core.session.FtpSession;
import org.waarp.ftp.filesystembased.FilesystemBasedFtpAuth;
import org.waarp.ftp.simpleimpl.config.FileBasedConfiguration;

import java.io.File;

/**
 * FtpAuth implementation based on a list of (user/password/account) stored in a
 * xml file load at startup from
 * configuration. Not to be used in production!
 */
public class FileBasedAuth extends FilesystemBasedFtpAuth {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(FileBasedAuth.class);

  /**
   * Current authentication
   */
  private SimpleAuth currentAuth;

  /**
   * @param session
   */
  public FileBasedAuth(final FtpSession session) {
    super(session);
  }

  @Override
  protected final void businessClean() {
    currentAuth = null;
  }

  /**
   * @param user the user to set
   *
   * @return (NOOP, 230) if the user is OK, else return the following command
   *     that must follow (usually PASS) and
   *     the associated reply
   *
   * @throws Reply421Exception if there is a problem during the
   *     authentication
   * @throws Reply530Exception if there is a problem during the
   *     authentication
   */
  @Override
  protected final NextCommandReply setBusinessUser(final String user)
      throws Reply530Exception {
    final SimpleAuth auth =
        ((FileBasedConfiguration) ((FtpSession) getSession()).getConfiguration()).getSimpleAuth(
            user);
    if (auth == null) {
      setIsIdentified(false);
      currentAuth = null;
      throw new Reply530Exception("User name not allowed");
    }
    currentAuth = auth;
    // logger.debug("User: {}", user)
    return new NextCommandReply(FtpCommandCode.PASS,
                                ReplyCode.REPLY_331_USER_NAME_OKAY_NEED_PASSWORD,
                                null);
  }

  /**
   * Set the password according to any implementation and could set the
   * rootFromAuth. If NOOP is returned,
   * isIdentifed must be TRUE. A special case is implemented for test user.
   *
   * @param password
   *
   * @return (NOOP, 230) if the Password is OK, else return the following
   *     command that must follow (usually ACCT)
   *     and the associated reply
   *
   * @throws Reply421Exception if there is a problem during the
   *     authentication
   * @throws Reply530Exception if there is a problem during the
   *     authentication
   */
  @Override
  protected final NextCommandReply setBusinessPassword(final String password)
      throws Reply421Exception, Reply530Exception {
    if (currentAuth == null) {
      setIsIdentified(false);
      throw new Reply530Exception("PASS needs a USER first");
    }
    if (currentAuth.isPasswordValid(password)) {
      if ("test".equals(user)) {
        // logger.debug("User test")
        try {
          return setAccount("test");
        } catch (final Reply502Exception ignored) {
          // nothing
        }
      }
      return new NextCommandReply(FtpCommandCode.ACCT,
                                  ReplyCode.REPLY_332_NEED_ACCOUNT_FOR_LOGIN,
                                  null);
    }
    throw new Reply530Exception("Password is not valid");
  }

  /**
   * Set the account according to any implementation and could set the
   * rootFromAuth. If NOOP is returned,
   * isIdentifed must be TRUE.
   *
   * @param account
   *
   * @return (NOOP, 230) if the Account is OK, else return the following
   *     command
   *     that must follow and the
   *     associated reply
   *
   * @throws Reply421Exception if there is a problem during the
   *     authentication
   * @throws Reply530Exception if there is a problem during the
   *     authentication
   */
  @Override
  protected final NextCommandReply setBusinessAccount(final String account)
      throws Reply530Exception {
    if (currentAuth == null) {
      throw new Reply530Exception("ACCT needs a USER first");
    }
    if (currentAuth.isAccountValid(account)) {
      // logger.debug("Account: {}", account)
      setIsIdentified(true);
      logger.info("User {} is authentified with account {}", user, account);
      return new NextCommandReply(FtpCommandCode.NOOP,
                                  ReplyCode.REPLY_230_USER_LOGGED_IN, null);
    }
    throw new Reply530Exception("Account is not valid");
  }

  @Override
  public final boolean isBusinessPathValid(final String newPath) {
    if (newPath == null) {
      return false;
    }
    return newPath.startsWith(getBusinessPath());
  }

  @Override
  protected final String setBusinessRootFromAuth() throws Reply421Exception {
    final String path;
    if (account == null) {
      path = DirInterface.SEPARATOR + user;
    } else {
      path = DirInterface.SEPARATOR + user + DirInterface.SEPARATOR + account;
    }
    final String fullpath = getAbsolutePath(path);
    final File file = new File(fullpath);
    if (!file.isDirectory()) {
      throw new Reply421Exception("Filesystem not ready");
    }
    return path;
  }

  @Override
  public final boolean isAdmin() {
    return currentAuth.isAdmin();
  }
}
