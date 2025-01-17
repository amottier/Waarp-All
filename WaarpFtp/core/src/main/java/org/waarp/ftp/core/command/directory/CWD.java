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
package org.waarp.ftp.core.command.directory;

import org.waarp.common.command.ReplyCode;
import org.waarp.common.command.exception.CommandAbstractException;
import org.waarp.common.command.exception.Reply530Exception;
import org.waarp.common.logging.WaarpLogger;
import org.waarp.common.logging.WaarpLoggerFactory;
import org.waarp.ftp.core.command.AbstractCommand;
import org.waarp.ftp.core.file.FtpDir;

/**
 * CWD command
 */
public class CWD extends AbstractCommand {
  /**
   * Internal Logger
   */
  private static final WaarpLogger logger =
      WaarpLoggerFactory.getLogger(CWD.class);

  @Override
  public final void exec() throws CommandAbstractException {
    final FtpDir current = getSession().getDir();
    if (current == null) {
      logger.warn("not identidied");
      throw new Reply530Exception("Not authentificated");
    }
    String nextDir = getArg();
    if (!hasArg()) {
      nextDir = "/";
    }
    if (current.changeDirectory(nextDir)) {
      getSession().setReplyCode(ReplyCode.REPLY_250_REQUESTED_FILE_ACTION_OKAY,
                                '"' + current.getPwd() +
                                "\" is the new current directory");
    } else {
      getSession().setReplyCode(ReplyCode.REPLY_550_REQUESTED_ACTION_NOT_TAKEN,
                                null);
    }
  }

}
