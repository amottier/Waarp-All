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
package org.waarp.gateway.ftp.config;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Circular Value used by passive connections to find the next valid port to
 * propose to the client.
 */
class CircularIntValue {
  private final Object syncObject = new Object();
  /**
   * Min value
   */
  private final int min;

  /**
   * Max value
   */
  private final int max;

  /**
   * Current Value
   */
  private final AtomicInteger current;

  /**
   * Create a circular range of values
   *
   * @param min
   * @param max
   */
  CircularIntValue(final int min, final int max) {
    this.min = min;
    this.max = max;
    current = new AtomicInteger(this.min - 1);
  }

  /**
   * Get the next value
   *
   * @return the next value
   */
  public final int getNext() {
    synchronized (syncObject) {
      if (!current.compareAndSet(max, min)) {
        current.incrementAndGet();
      }
      return current.get();
    }
  }
}
