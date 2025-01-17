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
package org.waarp.ftp.core.command.rfc3659;

import org.waarp.common.command.ReplyCode;
import org.waarp.common.command.exception.CommandAbstractException;
import org.waarp.common.command.exception.Reply501Exception;
import org.waarp.common.command.exception.Reply550Exception;
import org.waarp.ftp.core.command.AbstractCommand;
import org.waarp.ftp.core.file.FtpFile;

/**
 * SIZE command
 */
public class SIZE extends AbstractCommand {
  @Override
  public final void exec() throws CommandAbstractException {
    // First Check if any argument
    if (!hasArg()) {
      throw new Reply501Exception("Need a path as argument");
    }
    final String arg = getArg();
    if (!getSession().getDir().isFile(arg)) {
      throw new Reply550Exception("Not a file " + arg);
    }
    final FtpFile file = getSession().getDir().setFile(arg, false);
    final long length = file.length();
    getSession().setReplyCode(ReplyCode.REPLY_213_FILE_STATUS,
                              String.valueOf(length));
  }

}
