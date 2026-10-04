import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Myers' O(ND) diff: Part A (line diff) and Part B (changed-character ranges).
 *
 * Usage:
 *   java Main lines     A B
 *   java Main highlight A B
 */
public class Main {

    // ------------------------------------------------------------------
    // Matched runs: triples (i, j, length) meaning a[i..i+len) == b[j..j+len)
    // ------------------------------------------------------------------
    static final class Runs {
        int[] d = new int[96];
        int n = 0;                                   // number of runs

        void add(int i, int j, int len) {
            if (3 * n + 3 > d.length) {
                d = java.util.Arrays.copyOf(d, d.length * 2);
            }
            d[3 * n] = i;
            d[3 * n + 1] = j;
            d[3 * n + 2] = len;
            n++;
        }
    }

    // ------------------------------------------------------------------
    // Core: linear-space Myers (middle snake + divide and conquer)
    // ------------------------------------------------------------------

    /**
     * Middle snake of a[a0..a0+n) vs b[b0..b0+m). Returns {xs, ys, xe, ye} in
     * coordinates local to the sub-problem: the snake runs diagonally from
     * (xs, ys) to (xe, ye). Forward and reverse searches run alternately
     * until they overlap (Myers 1986, section 4b).
     */
    static int[] middleSnake(int[] a, int a0, int n, int[] b, int b0, int m) {
        final int delta = n - m;
        final boolean odd = (delta & 1) != 0;
        final int half = (n + m + 1) / 2;
        final int off = half + 1;                    // diagonal k lives at off + k
        final int[] vf = new int[2 * half + 3];      // furthest x, forward
        final int[] vb = new int[2 * half + 3];      // furthest x, reverse

        for (int d = 0; d <= half; d++) {
            // ---- forward pass: diagonals -d, -d+2, ..., d ----
            for (int k = -d; k <= d; k += 2) {
                int ki = off + k;
                int x;
                if (k == -d || (k != d && vf[ki - 1] < vf[ki + 1])) {
                    x = vf[ki + 1];                  // step down (insertion)
                } else {
                    x = vf[ki - 1] + 1;              // step right (deletion)
                }
                int y = x - k;
                final int sx = x, sy = y;
                while (x < n && y < m && a[a0 + x] == b[b0 + y]) {   // snake
                    x++;
                    y++;
                }
                vf[ki] = x;
                if (odd) {
                    int rk = delta - k;
                    if (rk >= -(d - 1) && rk <= d - 1 && x + vb[off + rk] >= n) {
                        return new int[] {sx, sy, x, y};
                    }
                }
            }
            // ---- reverse pass: same thing on the reversed sequences ----
            for (int k = -d; k <= d; k += 2) {
                int ki = off + k;
                int x;
                if (k == -d || (k != d && vb[ki - 1] < vb[ki + 1])) {
                    x = vb[ki + 1];
                } else {
                    x = vb[ki - 1] + 1;
                }
                int y = x - k;
                final int sx = x, sy = y;
                while (x < n && y < m && a[a0 + n - 1 - x] == b[b0 + m - 1 - y]) {
                    x++;
                    y++;
                }
                vb[ki] = x;
                if (!odd) {
                    int fk = delta - k;
                    if (fk >= -d && fk <= d && x + vf[off + fk] >= n) {
                        // convert the reverse snake back to forward coordinates
                        return new int[] {n - x, m - y, n - sx, m - sy};
                    }
                }
            }
        }
        throw new IllegalStateException("middle snake not found");   // cannot happen
    }

    /** Divide and conquer. Appends matched runs, in order, to out. */
    static void solve(int[] a, int a0, int a1, int[] b, int b0, int b1, Runs out) {
        // common prefix
        int i = a0, j = b0;
        while (i < a1 && j < b1 && a[i] == b[j]) {
            i++;
            j++;
        }
        if (i > a0) {
            out.add(a0, b0, i - a0);
        }
        a0 = i;
        b0 = j;
        // common suffix (emitted after the middle is solved, to keep order)
        int e = 0;
        while (a1 > a0 && b1 > b0 && a[a1 - 1] == b[b1 - 1]) {
            a1--;
            b1--;
            e++;
        }
        if (a0 < a1 && b0 < b1) {
            int[] s = middleSnake(a, a0, a1 - a0, b, b0, b1 - b0);
            solve(a, a0, a0 + s[0], b, b0, b0 + s[1], out);
            if (s[2] > s[0]) {
                out.add(a0 + s[0], b0 + s[1], s[2] - s[0]);
            }
            solve(a, a0 + s[2], a1, b, b0 + s[3], b1, out);
        }
        if (e > 0) {
            out.add(a1, b1, e);
        }
    }

    /**
     * Minimal diff of two int sequences whose values are in [0, k).
     * Items occurring in only one side can never match, so they are removed
     * first; the runs are then mapped back to original indices.
     */
    static Runs diffRuns(int[] a, int[] b, int k) {
        boolean[] inA = new boolean[k];
        boolean[] inB = new boolean[k];
        for (int x : a) inA[x] = true;
        for (int x : b) inB[x] = true;
        int na = 0, nb = 0;
        for (int x : a) if (inB[x]) na++;
        for (int x : b) if (inA[x]) nb++;
        int[] amap = new int[na], bmap = new int[nb];
        int[] fa = new int[na], fb = new int[nb];
        int p = 0;
        for (int i = 0; i < a.length; i++) {
            if (inB[a[i]]) {
                amap[p] = i;
                fa[p++] = a[i];
            }
        }
        p = 0;
        for (int j = 0; j < b.length; j++) {
            if (inA[b[j]]) {
                bmap[p] = j;
                fb[p++] = b[j];
            }
        }
        Runs tmp = new Runs();
        solve(fa, 0, na, fb, 0, nb, tmp);

        Runs res = new Runs();
        for (int r = 0; r < tmp.n; r++) {
            int i = tmp.d[3 * r], j = tmp.d[3 * r + 1], len = tmp.d[3 * r + 2];
            // a filtered run may span removed items: split where not contiguous
            int startI = amap[i], startJ = bmap[j], cnt = 1;
            int oi = startI, oj = startJ;
            for (int t = 1; t < len; t++) {
                int ni = amap[i + t], nj = bmap[j + t];
                if (ni == oi + 1 && nj == oj + 1) {
                    cnt++;
                } else {
                    res.add(startI, startJ, cnt);
                    startI = ni;
                    startJ = nj;
                    cnt = 1;
                }
                oi = ni;
                oj = nj;
            }
            res.add(startI, startJ, cnt);
        }
        return res;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------
    static byte[][] readLines(String path) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(path));
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(java.util.Arrays.copyOfRange(data, start, i));
                start = i + 1;
            }
        }
        // last piece: dropped if empty (file ended with \n or is empty)
        if (start < data.length) {
            lines.add(java.util.Arrays.copyOfRange(data, start, data.length));
        }
        return lines.toArray(new byte[0][]);
    }

    // ------------------------------------------------------------------
    // Part B helpers
    // ------------------------------------------------------------------

    /** Unmatched [start,end) ranges of a sequence of `len` code points. */
    static void appendRanges(StringBuilder sb, int len, Runs runs, int which) {
        int pos = 0;
        boolean any = false;
        for (int r = 0; r < runs.n; r++) {
            int s = runs.d[3 * r + which];
            if (s > pos) {
                if (any) sb.append(',');
                sb.append(pos).append('-').append(s);
                any = true;
            }
            pos = s + runs.d[3 * r + 2];
        }
        if (pos < len) {
            if (any) sb.append(',');
            sb.append(pos).append('-').append(len);
            any = true;
        }
        if (!any) sb.append('.');
    }

    /** The "? old | new" line for one paired - / + line. */
    static byte[] highlightLine(byte[] oldLine, byte[] newLine) {
        // iterate by code points, not by Java chars (an emoji is 2 chars)
        int[] oc = new String(oldLine, StandardCharsets.UTF_8).codePoints().toArray();
        int[] nc = new String(newLine, StandardCharsets.UTF_8).codePoints().toArray();
        HashMap<Integer, Integer> ids = new HashMap<>();
        int[] oa = new int[oc.length], nb = new int[nc.length];
        for (int i = 0; i < oc.length; i++) oa[i] = ids.computeIfAbsent(oc[i], z -> ids.size());
        for (int i = 0; i < nc.length; i++) nb[i] = ids.computeIfAbsent(nc[i], z -> ids.size());
        Runs runs = diffRuns(oa, nb, Math.max(1, ids.size()));
        StringBuilder sb = new StringBuilder("? ");
        appendRanges(sb, oc.length, runs, 0);
        sb.append(" | ");
        appendRanges(sb, nc.length, runs, 1);
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------
    static void writeLine(OutputStream out, int prefix, byte[] line) throws IOException {
        out.write(prefix);
        out.write(line);
        out.write('\n');
    }

    /** Prints one change block: all '-' first, then all '+' (delete-first rule). */
    static void changeBlock(OutputStream out, byte[][] a, int pi, int iEnd,
                            byte[][] b, int pj, int jEnd, boolean hl) throws IOException {
        for (int i = pi; i < iEnd; i++) {
            writeLine(out, '-', a[i]);
        }
        int paired = Math.min(iEnd - pi, jEnd - pj);
        for (int t = 0; t < jEnd - pj; t++) {
            writeLine(out, '+', b[pj + t]);
            if (hl && t < paired) {
                out.write(highlightLine(a[pi + t], b[pj + t]));
            }
        }
    }

    static void buildOutput(OutputStream out, byte[][] a, byte[][] b, boolean hl) throws IOException {
        // replace each distinct line by a small int id: much faster to compare
        HashMap<ByteBuffer, Integer> ids = new HashMap<>();
        int[] ia = new int[a.length], ib = new int[b.length];
        for (int i = 0; i < a.length; i++) {
            ia[i] = ids.computeIfAbsent(ByteBuffer.wrap(a[i]), z -> ids.size());
        }
        for (int j = 0; j < b.length; j++) {
            ib[j] = ids.computeIfAbsent(ByteBuffer.wrap(b[j]), z -> ids.size());
        }
        Runs runs = diffRuns(ia, ib, Math.max(1, ids.size()));

        int pi = 0, pj = 0;
        for (int r = 0; r < runs.n; r++) {
            int i = runs.d[3 * r], j = runs.d[3 * r + 1], len = runs.d[3 * r + 2];
            if (i > pi || j > pj) {
                changeBlock(out, a, pi, i, b, pj, j, hl);
            }
            for (int t = 0; t < len; t++) {
                writeLine(out, ' ', a[i + t]);
            }
            pi = i + len;
            pj = j + len;
        }
        if (pi < a.length || pj < b.length) {
            changeBlock(out, a, pi, a.length, b, pj, b.length, hl);
        }
    }

    public static void main(String[] args) {
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: Main lines|highlight A B");
            System.exit(2);
        }
        byte[][] a, b;
        try {
            a = readLines(args[1]);
            b = readLines(args[2]);
        } catch (IOException e) {
            System.err.println("error: cannot read file: " + e);
            System.exit(2);
            return;
        }
        try (OutputStream out = new BufferedOutputStream(System.out, 1 << 16)) {
            buildOutput(out, a, b, args[0].equals("highlight"));
        } catch (IOException e) {
            System.err.println("error: cannot write output: " + e);
            System.exit(1);
        }
    }
}
