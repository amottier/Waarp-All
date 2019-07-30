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
package org.waarp.openr66.context.authentication;

import org.waarp.common.command.NextCommandReply;
import org.waarp.common.command.exception.Reply421Exception;
import org.waarp.common.command.exception.Reply530Exception;
import org.waarp.common.database.DbSession;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.file.DirInterface;
import org.waarp.common.file.filesystembased.FilesystemBasedAuthImpl;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.common.role.RoleDefault;
import org.waarp.common.role.RoleDefault.ROLE;
import org.waarp.openr66.context.R66Session;
import org.waarp.openr66.database.data.DbHostAuth;
import org.waarp.openr66.protocol.configuration.Configuration;

import java.io.File;

/**
 *
 */
public class R66Auth extends FilesystemBasedAuthImpl {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(R66Auth.class);

  /**
   * Current authentication
   */
  private DbHostAuth currentAuth = null;
  /**
   * is Admin role
   */
  private boolean isAdmin = false;
  /**
   * Role set from configuration file only
   */
  private final RoleDefault role = new RoleDefault();

  /**
   * @param session
   */
  public R66Auth(R66Session session) {
    super(session);
  }

  @Override
  protected void businessClean() {
    currentAuth = null;
    isAdmin = false;
    role.clear();
  }

  @Override
  public String getBaseDirectory() {
    return Configuration.configuration.getBaseDirectory();
  }

  @Override
  protected NextCommandReply setBusinessPassword(String arg0)
      throws Reply421Exception, Reply530Exception {
    throw new Reply421Exception("Command not valid");
  }

  /**
   * @param dbSession
   * @param hostId
   * @param arg0
   *
   * @return True if the connection is OK (authentication is OK)
   *
   * @throws Reply530Exception if the authentication is wrong
   * @throws Reply421Exception If the service is not available
   */
  @Deprecated
  public boolean connection(DbSession dbSession, String hostId, byte[] arg0,
                            boolean isSsl)
      throws Reply530Exception, Reply421Exception {
    return connection(hostId, arg0, isSsl);
  }

  /**
   * @param hostId
   * @param arg0
   *
   * @return True if the connection is OK (authentication is OK)
   *
   * @throws Reply530Exception if the authentication is wrong
   * @throws Reply421Exception If the service is not available
   */
  public boolean connection(String hostId, byte[] arg0, boolean isSsl)
      throws Reply530Exception, Reply421Exception {
    DbHostAuth auth = null;
    try {
      auth = new DbHostAuth(hostId);
    } catch (final WaarpDatabaseException e) {
      logger.error("Cannot find authentication for " + hostId);
      setIsIdentified(false);
      currentAuth = null;
      throw new Reply530Exception("HostId not allowed");
    }
    if (auth.isSsl() != isSsl) {
      if (auth.isSsl()) {
        logger.error("Hostid {} must use SSL", hostId);
      } else {
        logger.error("Hostid {} cannot use SSL", hostId);
      }
      setIsIdentified(false);
      currentAuth = null;
      throw new Reply530Exception("HostId not allowed");
    }
    currentAuth = auth;
    role.clear();
    if (currentAuth.isKeyValid(arg0)) {
      setIsIdentified(true);
      user = hostId;
      setRootFromAuth();
      getSession().getDir().initAfterIdentification();
      isAdmin = currentAuth.isAdminrole();
      if (Configuration.configuration.getRoles().isEmpty()) {
        if (isAdmin) {
          role.setRole(ROLE.FULLADMIN);
        } else {
          role.setRole(ROLE.PARTNER);
        }
      } else {
        final RoleDefault configRole =
            Configuration.configuration.getRoles().get(hostId);
        if (configRole == null) {
          // set to default PARTNER
          role.setRole(ROLE.PARTNER);
        } else {
          role.setRoleDefault(configRole);
          if (role.isContaining(ROLE.FULLADMIN)) {
            isAdmin = true;
          }
        }
        if (isAdmin) {
          role.setRole(ROLE.FULLADMIN);
        }
      }
      logger.debug(role.toString());
      return true;
    }
    throw new Reply530Exception("Key is not valid for this HostId");
  }

  /**
   * @param key
   *
   * @return True if the key is valid for the current user
   */
  public boolean isKeyValid(byte[] key) {
    return currentAuth.isKeyValid(key);
  }

  /**
   * Set the root relative Path from current status of Authentication (should
   * be
   * the highest level for the
   * current authentication). If setBusinessRootFromAuth returns null, by
   * default set /user.
   *
   * @throws Reply421Exception if the business root is not available
   */
  private void setRootFromAuth() throws Reply421Exception {
    rootFromAuth = setBusinessRootFromAuth();
    if (rootFromAuth == null) {
      rootFromAuth = DirInterface.SEPARATOR;
    }
  }

  @Override
  protected String setBusinessRootFromAuth() throws Reply421Exception {
    final String path = null;
    final String fullpath = getAbsolutePath(path);
    final File file = new File(fullpath);
    if (!file.isDirectory()) {
      throw new Reply421Exception("Filesystem not ready");
    }
    return path;
  }

  @Override
  protected NextCommandReply setBusinessUser(String arg0)
      throws Reply421Exception, Reply530Exception {
    throw new Reply421Exception("Command not valid");
  }

  @Override
  public boolean isAdmin() {
    return isAdmin;
  }

  /**
   * @param roleCheck
   *
   * @return True if the current role contains the specified role to check
   */
  public boolean isValidRole(ROLE roleCheck) {
    return role.isContaining(roleCheck);
  }

  /**
   * @return True if the associated host is using SSL
   */
  public boolean isSsl() {
    return currentAuth.isSsl();
  }

  @Override
  public boolean isBusinessPathValid(String newPath) {
    if (newPath == null) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "Auth:" + isIdentified + " " +
           (currentAuth != null? currentAuth.toString() : "no Internal Auth") +
           " " + role.toString();
  }

  /**
   * @param dbSession
   * @param server
   *
   * @return the SimpleAuth if any for this user
   */
  @Deprecated
  public static DbHostAuth getServerAuth(DbSession dbSession, String server) {
    DbHostAuth auth = null;
    try {
      auth = new DbHostAuth(server);
    } catch (final WaarpDatabaseException e) {
      logger.warn("Cannot find the authentication " + server, e);
      return null;
    }
    return auth;
  }

  /**
   * Special Authentication for local execution
   *
   * @param isSSL
   * @param hostid
   */
  public void specialNoSessionAuth(boolean isSSL, String hostid) {
    isIdentified = true;
    DbHostAuth auth = null;
    try {
      auth = new DbHostAuth(hostid);
    } catch (final WaarpDatabaseException e1) {
    }
    if (auth == null) {
      auth =
          new DbHostAuth(hostid, "127.0.0.1", 6666, isSSL, null, true, false);
    }
    role.clear();
    currentAuth = auth;
    setIsIdentified(true);
    user = auth.getHostid();
    try {
      setRootFromAuth();
    } catch (final Reply421Exception e) {
    }
    getSession().getDir().initAfterIdentification();
    isAdmin = isSSL;
    if (isSSL) {
      role.setRole(ROLE.FULLADMIN);
      user = Configuration.configuration.getADMINNAME();
    }
  }

  /**
   * connection from HTTPS (no default rights, must be set either as admin or
   * specifically through ROLEs). Only
   * "false client" with port with negative values are allowed.
   *
   * @param dbSession
   * @param hostId
   * @param arg0
   *
   * @return True if the connection is OK (authentication is OK)
   *
   * @throws Reply530Exception if the authentication is wrong
   * @throws Reply421Exception If the service is not available
   */
  public boolean connectionHttps(DbSession dbSession, String hostId,
                                 byte[] arg0)
      throws Reply530Exception, Reply421Exception {
    final DbHostAuth auth = R66Auth.getServerAuth(dbSession, hostId);
    if (auth == null) {
      logger.error("Cannot find authentication for " + hostId);
      setIsIdentified(false);
      currentAuth = null;
      throw new Reply530Exception("HostId not allowed");
    }
    if (auth.getPort() >= 0) {
      logger.error("Authentication is unacceptable for " + hostId);
      setIsIdentified(false);
      currentAuth = null;
      throw new Reply530Exception("HostId not allowed");
    }
    role.clear();
    currentAuth = auth;
    if (currentAuth.isKeyValid(arg0)) {
      setIsIdentified(true);
      user = hostId;
      setRootFromAuth();
      getSession().getDir().initAfterIdentification();
      isAdmin = currentAuth.isAdminrole();
      if (Configuration.configuration.getRoles().isEmpty()) {
        if (isAdmin) {
          role.setRole(ROLE.FULLADMIN);
        }
      } else {
        final RoleDefault configRole =
            Configuration.configuration.getRoles().get(hostId);
        if (configRole != null) {
          role.setRoleDefault(configRole);
          if (role.isContaining(ROLE.FULLADMIN)) {
            isAdmin = true;
          }
        }
        if (isAdmin) {
          role.setRole(ROLE.FULLADMIN);
        }
      }
      logger.info(role.toString() + ":" + currentAuth);
      return true;
    }
    throw new Reply530Exception("Key is not valid for this HostId");
  }

  /**
   * @return a copy of the Role of the current authenticated partner
   */
  public RoleDefault getRole() {
    return new RoleDefault().setRoleDefault(role);
  }
}
