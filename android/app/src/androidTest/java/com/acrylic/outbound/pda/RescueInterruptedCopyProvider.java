package com.acrylic.outbound.pda;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test APK's separate process has no target Kotlin runtime. Never packaged in the product. */
public class RescueInterruptedCopyProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context app = getContext();
        String[] callers = app.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (!"ranchu".equals(android.os.Build.HARDWARE) || callers == null ||
                !Arrays.asList(callers).contains("com.acrylic.outbound.pda.debug") ||
                !"/encrypted-copy".equals(uri.getPath())) throw new SecurityException("Isolated test caller only");
        File target = new File(app.getFilesDir(), "interrupted-copy.pdarescue");
        if (!"r".equals(mode)) return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_CREATE |
                ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> delayedReadback(app, target, pipe[1]), "synthetic-interrupted-readback").start();
            return pipe[0];
        } catch (java.io.IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    private void delayedReadback(Context app, File target, ParcelFileDescriptor pipe) {
        try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe);
             FileInputStream original = new FileInputStream(target)) {
            output.write(original.read()); output.flush();
            try (FileOutputStream marker = new FileOutputStream(new File(app.getFilesDir(), "copy-awaiting-kill.txt"))) {
                marker.write("Real target exists; only one byte returned, digest incomplete".getBytes(StandardCharsets.UTF_8));
            }
            Thread.sleep(120_000);
        } catch (java.io.IOException | InterruptedException error) { android.util.Log.i("RescueCopyFixture", "Fixture readback interrupted", error); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
