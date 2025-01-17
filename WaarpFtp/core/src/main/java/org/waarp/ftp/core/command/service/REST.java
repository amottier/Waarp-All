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
package org.waarp.ftp.core.command.service;

import org.waarp.common.command.ReplyCode;
import org.waarp.common.command.exception.CommandAbstractException;
import org.waarp.common.command.exception.Reply501Exception;
import org.waarp.ftp.core.command.AbstractCommand;

/**
 * REST command
 */
public class REST extends AbstractCommand {
  @Override
  public final void exec() throws CommandAbstractException {
    if (!hasArg()) {
      invalidCurrentCommand();
      throw new Reply501Exception("Need a Marker as argument");
    }
    final String marker = getArg();
    if (getSession().getRestart().restartMarker(marker)) {
      getSession().setReplyCode(
          ReplyCode.REPLY_350_REQUESTED_FILE_ACTION_PENDING_FURTHER_INFORMATION,
          null);
      return;
    }
    // Marker in error
    throw new Reply501Exception("Marker is not allowed");
  }

}
