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
package org.waarp.openr66.protocol.localhandler.packet.json;

import org.waarp.openr66.protocol.localhandler.packet.LocalPacketFactory;

/**
 * File name or size changing Request JSON packet
 */
public class RequestJsonPacket extends JsonPacket {

  protected String filename;
  protected long filesize = -1;
  protected String fileInfo;

  /**
   * @return the filename
   */
  public final String getFilename() {
    return filename;
  }

  /**
   * @param filename the filename to set
   */
  public final void setFilename(final String filename) {
    this.filename = filename;
  }

  /**
   * @return the filesize
   */
  public final long getFilesize() {
    return filesize;
  }

  /**
   * @param filesize the filesize to set
   */
  public final void setFilesize(final long filesize) {
    this.filesize = filesize;
  }

  /**
   * @return the fileInfo
   */
  public final String getFileInfo() {
    return fileInfo;
  }

  /**
   * @param fileInfo the fileInfo to set
   */
  public final void setFileInfo(final String fileInfo) {
    this.fileInfo = fileInfo;
  }

  @Override
  public final void setRequestUserPacket() {
    setRequestUserPacket(LocalPacketFactory.REQUESTPACKET);
  }
}
