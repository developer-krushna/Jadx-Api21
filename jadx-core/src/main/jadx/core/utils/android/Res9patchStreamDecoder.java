/**
 * Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jadx.core.utils.android;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import org.jetbrains.annotations.Nullable;

import jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * @author Ryszard Wiśniewski "brut.alll@gmail.com"
 */
public class Res9patchStreamDecoder {

	public boolean decode(InputStream in, OutputStream out) {
		try {
			
			return true;
		} catch (Exception e) {
			throw new JadxRuntimeException("9patch image decode error", e);
		}
	}

	@Nullable
	private NinePatch getNinePatch(InputStream in) throws IOException {
		ExtDataInput di = new ExtDataInput(in);
		if (!find9patchChunk(di)) {
			return null;
		}
		return NinePatch.decode(di);
	}

	private boolean find9patchChunk(DataInput di) throws IOException {
		di.skipBytes(8);
		while (true) {
			int size;
			try {
				size = di.readInt();
			} catch (IOException ex) {
				return false;
			}
			if (di.readInt() == NP_CHUNK_TYPE) {
				return true;
			}
			di.skipBytes(size + 4);
		}
	}

	

	private static final int NP_CHUNK_TYPE = 0x6e705463; // npTc
	private static final int NP_COLOR = 0xff000000;

	private static class NinePatch {
		public final int padLeft;
		public final int padRight;
		public final int padTop;
		public final int padBottom;
		public final int[] xDivs;
		public final int[] yDivs;

		public NinePatch(int padLeft, int padRight, int padTop, int padBottom,
				int[] xDivs, int[] yDivs) {
			this.padLeft = padLeft;
			this.padRight = padRight;
			this.padTop = padTop;
			this.padBottom = padBottom;
			this.xDivs = xDivs;
			this.yDivs = yDivs;
		}

		public static NinePatch decode(ExtDataInput di) throws IOException {
			di.skipBytes(1);
			byte numXDivs = di.readByte();
			byte numYDivs = di.readByte();
			di.skipBytes(1);
			di.skipBytes(8);
			int padLeft = di.readInt();
			int padRight = di.readInt();
			int padTop = di.readInt();
			int padBottom = di.readInt();
			di.skipBytes(4);
			int[] xDivs = di.readIntArray(numXDivs);
			int[] yDivs = di.readIntArray(numYDivs);

			return new NinePatch(padLeft, padRight, padTop, padBottom, xDivs, yDivs);
		}
	}
}
