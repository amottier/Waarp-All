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

import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;

/**
 * Simple Authentication based on a previously load XML file. Not to be used in
 * production!
 */
public class SimpleAuth {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(SimpleAuth.class);

  /**
   * User name
   */
  private final String user;

  /**
   * Password
   */
  private final String password;

  /**
   * Multiple accounts
   */
  private final String[] accounts;

  /**
   * Is the current user an administrator (which can shutdown or change
   * bandwidth limitation)
   */
  private boolean isAdmin;

  /**
   * @param user
   * @param password
   * @param accounts
   */
  public SimpleAuth(final String user, final String password,
                    final String[] accounts) {
    this.user = user;
    this.password = password;
    this.accounts = accounts;
  }

  /**
   * Is the given password a valid one
   *
   * @param newpassword
   *
   * @return True if the password is valid (or any password is valid)
   */
  public final boolean isPasswordValid(final String newpassword) {
    if (password == null) {
      return true;
    }
    if (newpassword == null) {
      return false;
    }
    return password.equals(newpassword);
  }

  /**
   * Is the given account a valid one
   *
   * @param account
   *
   * @return True if the account is valid (or any account is valid)
   */
  public final boolean isAccountValid(final String account) {
    if (accounts == null) {
      logger.debug("No account needed");
      return true;
    }
    if (account == null) {
      logger.info("No account given");
      return false;
    }
    for (final String acct : accounts) {
      if (acct.equals(account)) {
        logger.debug("Account found");
        return true;
      }
    }
    logger.info("No account found");
    return false;
  }

  /**
   * @param isAdmin True if the user should be an administrator
   */
  public final void setAdmin(final boolean isAdmin) {
    this.isAdmin = isAdmin;
  }

  /**
   * @return the isAdmin
   */
  public final boolean isAdmin() {
    return isAdmin;
  }

  /**
   * @return the user
   */
  public final String getUser() {
    return user;
  }
}
