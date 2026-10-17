/**
 * JNative Windows Task Monitor - Performance and Settings pages (custom drawn).
 *
 * @author 张晓达
 */
class TmPerf {
    static int sel = 0;
    static int coreView = 0;
    static int showKernel = 0;
    static int listW = 220;

    /* item model rebuilt each paint */
    static int nItems = 0;
    static int[] kind = new int[24];
    static int[] idx = new int[24];

    static int cCpu() {
        return Th.rgb(17, 125, 187);
    }

    static int cMem() {
        return Th.rgb(139, 18, 174);
    }

    static int cDisk() {
        return Th.rgb(77, 166, 12);
    }

    static int cNet() {
        return Th.rgb(167, 79, 1);
    }

    static int cGpu() {
        return Th.rgb(17, 125, 187);
    }

    static void buildItems() {
        nItems = 0;
        kind[nItems] = 0;
        idx[nItems] = 0;
        nItems = nItems + 1;
        kind[nItems] = 1;
        idx[nItems] = 0;
        nItems = nItems + 1;
        int i = 0;
        while (i < TmStore.disks.size() && nItems < 22) {
            kind[nItems] = 2;
            idx[nItems] = i;
            nItems = nItems + 1;
            i = i + 1;
        }
        i = 0;
        while (i < TmStore.nets.size() && nItems < 22) {
            kind[nItems] = 3;
            idx[nItems] = i;
            nItems = nItems + 1;
            i = i + 1;
        }
        i = 0;
        while (i < TmStore.gpus.size() && nItems < 22) {
            kind[nItems] = 4;
            idx[nItems] = i;
            nItems = nItems + 1;
            i = i + 1;
        }
        if (sel >= nItems) {
            sel = 0;
        }
    }

    static String itemName(int n) {
        int k = kind[n];
        int i = idx[n];
        if (k == 0) {
            return "CPU";
        }
        if (k == 1) {
            return "内存";
        }
        if (k == 2) {
            TmDisk d = TmStore.disks.get(i);
            String s = "磁盘 " + String.valueOf(d.id);
            if (d.letters != null && d.letters.length() > 0) {
                s = s + " (" + d.letters + ")";
            }
            return s;
        }
        if (k == 3) {
            TmNet nt = TmStore.nets.get(i);
            return nt.name;
        }
        TmGpu g = TmStore.gpus.get(i);
        return "GPU " + String.valueOf(i);
    }

    static String itemSub(int n) {
        int k = kind[n];
        int i = idx[n];
        if (k == 0) {
            return Fmt.pctWhole(TmStore.cpuTotalX10) + "  " + Fmt.ghz(TmStore.cpuMhz);
        }
        if (k == 1) {
            return Fmt.gb(TmStore.memInUse) + "/" + Fmt.gb(TmStore.memTotal) + " ("
                    + Fmt.pctWhole((int) ((TmStore.memInUse * 1000) / Math.max(1L, TmStore.memTotal))) + ")";
        }
        if (k == 2) {
            TmDisk d = TmStore.disks.get(i);
            String m = d.media;
            if (m.length() == 0) {
                m = "磁盘";
            }
            return m + "  " + Fmt.pctWhole(d.actX10);
        }
        if (k == 3) {
            TmNet nt = TmStore.nets.get(i);
            String kd = "以太网";
            if (nt.kind.equals("wifi")) {
                kd = "Wi-Fi";
            }
            return kd + "  发送: " + Fmt.kbps(nt.sendBps) + " 接收: " + Fmt.kbps(nt.recvBps) + " Kbps";
        }
        TmGpu g = TmStore.gpus.get(i);
        return g.name + "  " + Fmt.pctWhole(g.utilX10);
    }

    static int itemColor(int n) {
        int k = kind[n];
        if (k == 0) {
            return cCpu();
        }
        if (k == 1) {
            return cMem();
        }
        if (k == 2) {
            return cDisk();
        }
        if (k == 3) {
            return cNet();
        }
        return cGpu();
    }

    static void miniGraph(int n, int x, int y, int w, int h) {
        int c = itemColor(n);
        int k = kind[n];
        int i = idx[n];
        Gfx.fill(x, y, w, h, Th.graphBg);
        if (k == 0) {
            miniLine(TmStore.hCpu, 0, 1000, x, y, w, h, c);
        } else if (k == 1) {
            miniLine(TmStore.hMem, 0, 1000, x, y, w, h, c);
        } else if (k == 2) {
            miniLine(TmStore.disks.get(i).hAct, 0, 1000, x, y, w, h, c);
        } else if (k == 3) {
            TmNet nt = TmStore.nets.get(i);
            long mx = Math.max(100000L, Math.max(maxOf(nt.hSend), maxOf(nt.hRecv)));
            miniLine(nt.hRecv, 0, mx, x, y, w, h, c);
            miniLine(nt.hSend, 0, mx, x, y, w, h, Th.argb(255, 255, 255, 0));
        } else {
            miniLine(TmStore.gpus.get(i).hUtil, 0, 1000, x, y, w, h, c);
        }
        Gfx.roundStroke(x, y, w, h, 0, 0, c, 10);
    }

    static void miniLine(long[] data, int base, long maxV, int x, int y, int w, int h, int color) {
        if (maxV <= 0) {
            maxV = 1;
        }
        Gfx.polyBegin();
        int i = 0;
        while (i < 60) {
            long v = data[base + i];
            if (v < 0) {
                v = 0;
            }
            if (v > maxV) {
                v = maxV;
            }
            long px = (long) x * 1000L + ((long) (w - 1) * 1000L * (long) i) / 59L;
            long py = (long) (y + h - 1) * 1000L - ((long) (h - 2) * 1000L * v) / maxV;
            Gfx.polyPt((int) px, (int) py);
            i = i + 1;
        }
        Gfx.polyEnd(0, color, 10);
    }

    static long maxOf(long[] a) {
        long m = 0;
        int i = 0;
        while (i < 60) {
            if (a[i] > m) {
                m = a[i];
            }
            i = i + 1;
        }
        return m;
    }

    /** Smoothly rounded axis max for byte-rate graphs. */
    static long niceMax(long m) {
        long base = 102400L;
        while (base < m) {
            base = base * 2;
        }
        return base;
    }

    static void stat(String label, String value, int x, int y, int w) {
        Gfx.text(label, x, y, w, 18, Th.sub, Th.fSmall, 4 + 8);
        Gfx.text(value, x, y + 18, w, 30, Th.text, Th.fStat, 4 + 8);
    }

    static void kv(String label, String value, int x, int y, int lw, int vw) {
        Gfx.text(label, x, y, lw, 20, Th.sub, Th.fBody, 4 + 8);
        Gfx.text(value, x + lw, y, vw, 20, Th.text, Th.fBody, 4 + 8);
    }

    static void graphFrame(String leftLabel, String rightLabel, int x, int y, int w, int h) {
        Gfx.text(leftLabel, x, y, w - 60, 18, Th.sub, Th.fSmall, 4 + 8);
        Gfx.text(rightLabel, x + w - 120, y, 120, 18, Th.sub, Th.fSmall, 2 + 4 + 8);
    }

    /* ---------------------------------------------------------------- paint */
    static void paint(int x, int y, int w, int h) {
        buildItems();
        int lw = listW;
        if (w < 640) {
            lw = 150;
        }
        /* resource list */
        int iy = y + 4;
        int ih = 66;
        int i = 0;
        while (i < nItems) {
            int sy = iy + i * (ih + 2);
            int hov = TmUi.hot(x + 6, sy, lw - 12, ih);
            if (i == sel) {
                Gfx.round(x + 6, sy, lw - 12, ih, 6, Th.navSel);
                Gfx.round(x + 6, sy + 16, 3, ih - 32, 2, Th.accent);
            } else if (hov != 0) {
                Gfx.round(x + 6, sy, lw - 12, ih, 6, Th.hover);
            }
            if (lw >= 190) {
                miniGraph(i, x + 18, sy + 12, 58, ih - 24);
                Gfx.text(itemName(i), x + 88, sy + 8, lw - 100, 22, Th.text, Th.fBold, 4 + 8);
                Gfx.text(itemSub(i), x + 88, sy + 30, lw - 96, 30, Th.sub, Th.fSmall, 16 + 8);
            } else {
                miniGraph(i, x + 14, sy + 8, 52, 24);
                Gfx.text(itemName(i), x + 14, sy + 34, lw - 24, 20, Th.text, Th.fSmall, 4 + 8);
            }
            TmHit.add(x + 6, sy, lw - 12, ih, 200, i);
            i = i + 1;
        }
        int px = x + lw + 12;
        int pw = w - lw - 12 - 24;
        int py = y + 4;
        int ph = h - 12;
        if (pw < 200) {
            return;
        }
        int k = kind[sel];
        int di = idx[sel];
        if (k == 0) {
            paintCpu(px, py, pw, ph);
        } else if (k == 1) {
            paintMem(px, py, pw, ph);
        } else if (k == 2) {
            paintDisk(px, py, pw, ph, TmStore.disks.get(di));
        } else if (k == 3) {
            paintNet(px, py, pw, ph, TmStore.nets.get(di));
        } else {
            paintGpu(px, py, pw, ph, TmStore.gpus.get(di), di);
        }
    }

    static void head(String title, String right, int x, int y, int w, int color) {
        Gfx.text(title, x, y, w, 32, Th.text, Th.fTitle, 4 + 8);
        int rw = Gfx.textWidth(Th.fMed, right);
        Gfx.text(right, x + w - rw - 4, y, rw + 4, 32, Th.sub, Th.fMed, 2 + 4 + 8);
    }

    static void paintCpu(int x, int y, int w, int h) {
        head("CPU", TmStore.cpuName, x, y, w, cCpu());
        int statsH = 150;
        int gy = y + 58;
        int gh = Math.max(110, Math.min(300, h - 58 - statsH - 28));
        String mode = "% 利用率";
        if (coreView != 0) {
            mode = "% 利用率 — 逻辑处理器 (" + String.valueOf(TmStore.ncpu) + ")";
        }
        graphFrame(mode, "100%", x, gy - 20, w, gh);
        if (coreView != 0) {
            int n = TmStore.ncpu;
            int cols = 1;
            while (cols * cols < n * 2 && cols < n) {
                cols = cols + 1;
            }
            int rowsN = (n + cols - 1) / cols;
            int cw = w / cols;
            int ch = gh / rowsN;
            int ci = 0;
            while (ci < n) {
                int cx = x + (ci % cols) * cw;
                int cy = gy + (ci / cols) * ch;
                TmUi.graph(cx + 1, cy + 1, cw - 3, ch - 3, TmStore.hCore, ci * 60, 60, 1000, cCpu(),
                        Th.argb(17, 125, 187, 50));
                ci = ci + 1;
            }
        } else {
            TmUi.graph(x, gy, w, gh, TmStore.hCpu, 0, 60, 1000, cCpu(), Th.argb(17, 125, 187, 50));
            if (showKernel != 0) {
                Gfx.polyBegin();
                int i = 0;
                while (i < 60) {
                    long v = Math.min(1000L, TmStore.hKernel[i]);
                    long px = (long) x * 1000L + ((long) w * 1000L * (long) i) / 59L;
                    long py = (long) (gy + gh) * 1000L - ((long) gh * 1000L * v) / 1000L;
                    Gfx.polyPt((int) px, (int) py);
                    i = i + 1;
                }
                Gfx.polyEnd(0, Th.argb(17, 125, 187, 120), 10);
            }
        }
        TmHit.add(x, gy, w, gh, 210, 0);
        Gfx.text("60 秒", x, gy + gh + 2, 80, 18, Th.sub, Th.fSmall, 4);
        Gfx.text("0", x + w - 40, gy + gh + 2, 40, 18, Th.sub, Th.fSmall, 2 + 4);
        int sy = gy + gh + 28;
        int c1 = x;
        stat("利用率", Fmt.pctWhole(TmStore.cpuTotalX10), c1, sy, 110);
        stat("速度", Fmt.ghz(TmStore.cpuMhz), c1 + 120, sy, 130);
        stat("进程", Fmt.nonNeg((long) TmStore.procCount), c1, sy + 54, 110);
        stat("线程", Fmt.nonNeg((long) TmStore.threadCount), c1 + 120, sy + 54, 110);
        stat("句柄", Fmt.nonNeg((long) TmStore.handleCount), c1 + 240, sy + 54, 110);
        stat("正常运行时间", Fmt.uptime(TmStore.uptimeSec), c1, sy + 108, 220);
        int rx = x + Math.max(380, w / 2 + 20);
        int rlw = 120;
        int rvw = Math.max(80, x + w - rx - rlw);
        kv("基准速度:", Fmt.ghz(TmStore.cpuBaseMhz), rx, sy, rlw, rvw);
        kv("插槽:", Fmt.nonNeg((long) TmStore.sockets), rx, sy + 22, rlw, rvw);
        kv("内核:", Fmt.nonNeg((long) TmStore.cores), rx, sy + 44, rlw, rvw);
        kv("逻辑处理器:", Fmt.nonNeg((long) TmStore.logical), rx, sy + 66, rlw, rvw);
        String vs = "—";
        if (TmStore.virt == 1) {
            vs = "已启用";
        } else if (TmStore.virt == 0) {
            vs = "已禁用";
        }
        kv("虚拟化:", vs, rx, sy + 88, rlw, rvw);
        kv("L1 缓存:", cacheText(TmStore.l1), rx, sy + 110, rlw, rvw);
        kv("L2 缓存:", cacheText(TmStore.l2), rx, sy + 132, rlw, rvw);
        kv("L3 缓存:", cacheText(TmStore.l3), rx, sy + 154, rlw, rvw);
    }

    static String cacheText(int kb) {
        if (kb <= 0) {
            return "—";
        }
        if (kb >= 1024) {
            return Fmt.dec1(((long) kb * 10 + 512) / 1024) + " MB";
        }
        return String.valueOf(kb) + " KB";
    }

    static void paintMem(int x, int y, int w, int h) {
        head("内存", Fmt.gb(TmStore.memTotal), x, y, w, cMem());
        int gy = y + 78;
        int gh = Math.max(100, Math.min(260, h - 78 - 170 - 90));
        graphFrame("内存使用量", Fmt.gb(TmStore.memTotal), x, gy - 20, w, gh);
        TmUi.graph(x, gy, w, gh, TmStore.hMem, 0, 60, 1000, cMem(), Th.argb(139, 18, 174, 50));
        Gfx.text("60 秒", x, gy + gh + 2, 80, 18, Th.sub, Th.fSmall, 4);
        Gfx.text("0", x + w - 40, gy + gh + 2, 40, 18, Th.sub, Th.fSmall, 2 + 4);
        int by = gy + gh + 30;
        Gfx.text("内存组合", x, by, 200, 18, Th.sub, Th.fSmall, 4);
        int bh = 36;
        long tot = Math.max(1L, TmStore.memTotal);
        int wInUse = (int) (((long) w * TmStore.memInUse) / tot);
        int wCache = (int) (((long) w * Math.min(TmStore.cached, TmStore.memTotal - TmStore.memInUse)) / tot);
        Gfx.fill(x, by + 20, w, bh, Th.graphBg);
        Gfx.fill(x, by + 20, wInUse, bh, Th.argb(139, 18, 174, 90));
        Gfx.fill(x + wInUse, by + 20, wCache, bh, Th.argb(139, 18, 174, 36));
        Gfx.roundStroke(x, by + 20, w, bh, 0, 0, cMem(), 10);
        int sy = by + 20 + bh + 14;
        stat("使用中(已压缩)", Fmt.gb(TmStore.memInUse) + " (" + Fmt.gb(TmStore.compressed) + ")", x, sy, 230);
        stat("可用", Fmt.gb(TmStore.memAvail), x + 240, sy, 130);
        stat("已提交", Fmt.gb(TmStore.commitUsed) + "/" + Fmt.gb(TmStore.commitLimit), x, sy + 54, 230);
        stat("已缓存", Fmt.gb(TmStore.cached), x + 240, sy + 54, 130);
        int rx = x + Math.max(380, w / 2 + 40);
        int rlw = 150;
        int rvw = Math.max(80, x + w - rx - rlw);
        String sp = "—";
        if (TmStore.memSpeed > 0) {
            sp = String.valueOf(TmStore.memSpeed) + " MHz";
        }
        kv("速度:", sp, rx, sy, rlw, rvw);
        String sl = "—";
        if (TmStore.slotsTotal > 0) {
            sl = String.valueOf(TmStore.slotsUsed) + " / " + String.valueOf(TmStore.slotsTotal);
        }
        kv("已使用的插槽:", sl, rx, sy + 22, rlw, rvw);
        String ff = TmStore.formFactor;
        if (ff.length() == 0) {
            ff = "—";
        }
        kv("外形规格:", ff, rx, sy + 44, rlw, rvw);
        kv("分页缓冲池:", Fmt.gb(TmStore.pagedPool), rx, sy + 66, rlw, rvw);
        kv("非分页缓冲池:", Fmt.gb(TmStore.nonPagedPool), rx, sy + 88, rlw, rvw);
    }

    static void paintDisk(int x, int y, int w, int h, TmDisk d) {
        String title = "磁盘 " + String.valueOf(d.id);
        if (d.letters != null && d.letters.length() > 0) {
            title = title + " (" + d.letters + ")";
        }
        head(title, d.model, x, y, w, cDisk());
        int gh = Math.max(70, Math.min(150, (h - 58 - 150) / 2 - 24));
        int gy = y + 78;
        graphFrame("活动时间", "100%", x, gy - 20, w, gh);
        TmUi.graph(x, gy, w, gh, d.hAct, 0, 60, 1000, cDisk(), Th.argb(77, 166, 12, 50));
        int gy2 = gy + gh + 44;
        long mx = niceMax(Math.max(maxOf(d.hRead), maxOf(d.hWrite)));
        graphFrame("磁盘传输速率", Fmt.rate(mx), x, gy2 - 20, w, gh);
        TmUi.graph(x, gy2, w, gh, d.hRead, 0, 60, mx, cDisk(), Th.argb(77, 166, 12, 50));
        Gfx.polyBegin();
        int i = 0;
        while (i < 60) {
            long v = Math.min(mx, d.hWrite[i]);
            long px = (long) x * 1000L + ((long) w * 1000L * (long) i) / 59L;
            long py = (long) (gy2 + gh) * 1000L - ((long) gh * 1000L * v) / mx;
            Gfx.polyPt((int) px, (int) py);
            i = i + 1;
        }
        Gfx.polyEnd(0, Th.argb(77, 166, 12, 130), 10);
        Gfx.text("60 秒", x, gy2 + gh + 2, 80, 18, Th.sub, Th.fSmall, 4);
        int sy = gy2 + gh + 28;
        stat("活动时间", Fmt.pctWhole(d.actX10), x, sy, 110);
        String resp = "—";
        if (d.respX10 >= 0) {
            resp = Fmt.dec1((long) d.respX10) + " 毫秒";
        }
        stat("平均响应时间", resp, x + 130, sy, 170);
        stat("读取速度", Fmt.rate(d.readBps), x, sy + 54, 170);
        stat("写入速度", Fmt.rate(d.writeBps), x + 190, sy + 54, 170);
        int rx = x + Math.max(430, w / 2 + 60);
        int rlw = 100;
        int rvw = Math.max(80, x + w - rx - rlw);
        kv("容量:", Fmt.gb(d.size), rx, sy, rlw, rvw);
        String md = d.media;
        if (md.length() == 0) {
            md = "—";
        }
        kv("类型:", md, rx, sy + 22, rlw, rvw);
    }

    static void paintNet(int x, int y, int w, int h, TmNet n) {
        head(n.name, n.desc, x, y, w, cNet());
        long mx = niceMax(Math.max(maxOf(n.hSend), maxOf(n.hRecv)));
        int gy = y + 78;
        int gh = Math.max(110, Math.min(280, h - 78 - 190));
        graphFrame("吞吐量", Fmt.bitRate(mx), x, gy - 20, w, gh);
        TmUi.graph(x, gy, w, gh, n.hRecv, 0, 60, mx, cNet(), Th.argb(167, 79, 1, 50));
        Gfx.polyBegin();
        int i = 0;
        while (i < 60) {
            long v = Math.min(mx, n.hSend[i]);
            long px = (long) x * 1000L + ((long) w * 1000L * (long) i) / 59L;
            long py = (long) (gy + gh) * 1000L - ((long) gh * 1000L * v) / mx;
            Gfx.polyPt((int) px, (int) py);
            i = i + 1;
        }
        Gfx.polyEnd(0, Th.argb(167, 79, 1, 130), 10);
        Gfx.text("60 秒", x, gy + gh + 2, 80, 18, Th.sub, Th.fSmall, 4);
        int sy = gy + gh + 28;
        stat("发送", Fmt.bitRate(n.sendBps), x, sy, 190);
        stat("接收", Fmt.bitRate(n.recvBps), x + 200, sy, 190);
        int rx = x + Math.max(430, w / 2 + 60);
        int rlw = 110;
        int rvw = Math.max(80, x + w - rx - rlw);
        kv("适配器名称:", n.name, rx, sy, rlw, rvw);
        String kd = "以太网";
        if (n.kind.equals("wifi")) {
            kd = "Wi-Fi";
        } else if (n.kind.equals("other")) {
            kd = "其他";
        }
        kv("连接类型:", kd, rx, sy + 22, rlw, rvw);
        kv("IPv4 地址:", n.ipv4, rx, sy + 44, rlw, rvw);
        kv("IPv6 地址:", n.ipv6, rx, sy + 66, rlw, rvw);
        kv("链路速度:", Fmt.linkSpeed(n.link), rx, sy + 88, rlw, rvw);
    }

    static void paintGpu(int x, int y, int w, int h, TmGpu g, int gi) {
        head("GPU " + String.valueOf(gi), g.name, x, y, w, cGpu());
        int gh = Math.max(70, Math.min(150, (h - 58 - 150) / 2 - 24));
        int gy = y + 78;
        graphFrame("利用率 (任一引擎最大值)", "100%", x, gy - 20, w, gh);
        TmUi.graph(x, gy, w, gh, g.hUtil, 0, 60, 1000, cGpu(), Th.argb(17, 125, 187, 50));
        int gy2 = gy + gh + 44;
        long vm = Math.max(1L, g.vramTotal);
        graphFrame("专用 GPU 内存", Fmt.gb(g.vramTotal), x, gy2 - 20, w, gh);
        TmUi.graph(x, gy2, w, gh, g.hVram, 0, 60, vm, cGpu(), Th.argb(17, 125, 187, 50));
        Gfx.text("60 秒", x, gy2 + gh + 2, 80, 18, Th.sub, Th.fSmall, 4);
        int sy = gy2 + gh + 28;
        stat("利用率", Fmt.pctWhole(g.utilX10), x, sy, 110);
        stat("专用 GPU 内存", Fmt.gb(g.vramUsed) + "/" + Fmt.gb(g.vramTotal), x + 130, sy, 220);
        stat("共享 GPU 内存", Fmt.gb(g.sharedUsed) + "/" + Fmt.gb(g.sharedTotal), x, sy + 54, 240);
        int rx = x + Math.max(430, w / 2 + 60);
        int rlw = 120;
        int rvw = Math.max(80, x + w - rx - rlw);
        String dv = g.driver;
        if (dv == null || dv.length() == 0) {
            dv = "—";
        }
        kv("驱动程序版本:", dv, rx, sy, rlw, rvw);
        kv("GPU 温度:", "—(系统未提供通用接口)", rx, sy + 22, rlw, rvw);
    }

    static void click(int item) {
        sel = item;
    }

    /* ------------------------------------------------------------- Settings */
    static void paintSettings(int x, int y, int w, int h) {
        int sx = x + 24;
        int sw = Math.min(760, w - 48);
        int yy = y + 8;
        yy = section("实时更新速度", yy, sx, sw);
        yy = radioRow("高 (每 0.5 秒)", TmApp.speed == 0, 300, 0, yy, sx, sw);
        yy = radioRow("正常 (每 1 秒)", TmApp.speed == 1, 300, 1, yy, sx, sw);
        yy = radioRow("低 (每 4 秒)", TmApp.speed == 2, 300, 2, yy, sx, sw);
        yy = radioRow("已暂停 (按 F5 手动刷新)", TmApp.speed == 3, 300, 3, yy, sx, sw);
        yy = yy + 12;
        yy = section("主题", yy, sx, sw);
        yy = radioRow("使用系统设置", Th.mode == 0, 301, 0, yy, sx, sw);
        yy = radioRow("浅色", Th.mode == 1, 301, 1, yy, sx, sw);
        yy = radioRow("深色", Th.mode == 2, 301, 2, yy, sx, sw);
        yy = yy + 12;
        yy = section("性能页", yy, sx, sw);
        yy = switchRow("按逻辑处理器显示 CPU 图表", coreView, 302, 0, yy, sx, sw);
        yy = switchRow("在 CPU 图表中显示内核时间", showKernel, 302, 1, yy, sx, sw);
        yy = yy + 12;
        yy = section("关于", yy, sx, sw);
        String who = TmStore.osCaption;
        if (TmStore.osBuild.length() > 0) {
            who = who + " (" + TmStore.osBuild + ")";
        }
        Gfx.text("JNative 任务管理器 — 使用 JNative 编译为原生程序，界面由 GDI/GDI+ 自绘，无 Swing/AWT/JRE。", sx, yy, sw, 22, Th.text, Th.fBody, 4 + 8);
        Gfx.text(who + "  ·  " + TmStore.hostName + "  ·  " + TmStore.userName, sx, yy + 24, sw, 22, Th.sub, Th.fBody, 4 + 8);
        String adm = "当前未以管理员身份运行(部分进程信息与操作不可用)";
        if (TmApp.elevated != 0) {
            adm = "当前已以管理员身份运行";
        }
        Gfx.text(adm, sx, yy + 48, sw, 22, Th.sub, Th.fBody, 4 + 8);
        Gfx.text("最近一次刷新耗时 " + AmParse.longToString(TmApp.lastRefreshMs) + " ms，上一帧绘制 " + AmParse.longToString(TmApp.lastPaintMs) + " ms，绘制异常 " + String.valueOf(TmApp.paintErrors) + " 次", sx, yy + 120, sw, 22, Th.faint, Th.fSmall, 4 + 8);
        if (TmApp.elevated == 0) {
            int bw = 200;
            Gfx.round(sx, yy + 80, bw, 32, 4, Th.btnBg);
            Gfx.roundStroke(sx, yy + 80, bw, 32, 4, 0, Th.line, 10);
            if (TmUi.hot(sx, yy + 80, bw, 32) != 0) {
                Gfx.round(sx, yy + 80, bw, 32, 4, Th.btnHover);
            }
            TmUi.textC("以管理员身份重新启动", sx, yy + 80, bw, 32, Th.text, Th.fBody);
            TmHit.add(sx, yy + 80, bw, 32, 303, 0);
        }
    }

    static int section(String t, int yy, int sx, int sw) {
        Gfx.text(t, sx, yy, sw, 28, Th.text, Th.fMed, 4 + 8);
        return yy + 32;
    }

    static int radioRow(String label, boolean on, int hitId, int data, int yy, int sx, int sw) {
        int o = 0;
        if (on) {
            o = 1;
        }
        if (TmUi.hot(sx, yy, sw, 32) != 0) {
            Gfx.round(sx, yy, sw, 32, 4, Th.hover);
        }
        TmUi.radio(sx + 8, yy + 6, o);
        Gfx.text(label, sx + 40, yy, sw - 48, 32, Th.text, Th.fBody, 4 + 8);
        TmHit.add(sx, yy, sw, 32, hitId, data);
        return yy + 34;
    }

    static int switchRow(String label, int on, int hitId, int data, int yy, int sx, int sw) {
        if (TmUi.hot(sx, yy, sw, 36) != 0) {
            Gfx.round(sx, yy, sw, 36, 4, Th.hover);
        }
        Gfx.text(label, sx + 8, yy, sw - 100, 36, Th.text, Th.fBody, 4 + 8);
        TmUi.toggle(sx + sw - 56, yy + 8, on);
        TmHit.add(sx, yy, sw, 36, hitId, data);
        return yy + 38;
    }
}
