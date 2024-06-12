/***
* Ribose is a recursive transduction engine for Java
*
* Copyright (C) 2011,2022 Kim Briggs
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program (LICENSE-gpl-3.0). If not, see
* <http://www.gnu.org/licenses/#GPL>.
*/

package com.characterforming.jrte.engine;

final class StateStack {
  private final int[] stack;
  private int tos;

  StateStack(int stateCount) {
    this.stack = new int[stateCount];
    this.tos = -1;
  }

  int size() { return this.tos + 1; }

  void push(int state) {
    for (int i = 0; i <= this.tos; i++) {
      if (this.stack[i] == state)
        return;
    }
    this.stack[++this.tos] = state;
  }

  int pop() {
    return this.tos >= 0 ? this.stack[this.tos--] : -1;
  }

  public boolean isEmpty() {
    return this.tos < 0;
  }
}