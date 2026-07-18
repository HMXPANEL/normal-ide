/************************************************************************************
 * This file is part of AndroidIDE.
 *
 *
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 *
 **************************************************************************************/

package com.hmx.ide.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

  public static String readFile(String path) {
    createNewFile(path);

    StringBuilder sb = new StringBuilder();
    FileReader fr = null;
    try {
      fr = new FileReader(new File(path));

      char[] buff = new char[1024];
      int length = 0;

      while ((length = fr.read(buff)) > 0) {
        sb.append(new String(buff, 0, length));
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fr != null) {
        try {
          fr.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    return sb.toString();
  }

  private static void createNewFile(String path) {
    int lastSep = path.lastIndexOf(File.separator);
    if (lastSep > 0) {
      String dirPath = path.substring(0, lastSep);
      makeDir(dirPath);
    }

    File file = new File(path);

    try {
      if (!file.exists()) file.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void makeDir(String path) {
    if (!isExistFile(path)) {
      File file = new File(path);
      file.mkdirs();
    }
  }

  public static boolean isExistFile(String path) {
    File file = new File(path);
    return file.exists();
  }

  public static List<String> readFileAsLineList(String path) {
    createNewFile(path);
    List<String> lines = new ArrayList<>();
    FileReader fr = null;
    try {
      fr = new FileReader(new File(path));
      char[] buff = new char[1024];
      int length = 0;
      while ((length = fr.read(buff)) > 0) {
        lines.add(new String(buff, 0, length));
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fr != null) {
        try {
          fr.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    return lines;
  }

  public static void writeFile(String path, String str) {
    createNewFile(path);

    try {
      writeFile(new File(path), str);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void writeFile(File path, String str) throws IOException {
    FileWriter fileWriter = new FileWriter(path, false);
    fileWriter.write(str);
    fileWriter.flush();
    fileWriter.close();
  }

  public static void copyFile(String sourcePath, String destPath) {
    if (!isExistFile(sourcePath)) return;
    createNewFile(destPath);

    FileInputStream fis = null;
    FileOutputStream fos = null;

    try {
      fis = new FileInputStream(sourcePath);
      fos = new FileOutputStream(destPath, false);

      byte[] buff = new byte[1024];
      int length = 0;

      while ((length = fis.read(buff)) > 0) {
        fos.write(buff, 0, length);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void moveFile(String sourcePath, String destPath) {
    copyFile(sourcePath, destPath);
    deleteFile(sourcePath);
  }

  public static void deleteFile(String path) {
    File file = new File(path);

    if (!file.exists()) return;

    if (file.isFile()) {
      file.delete();
      return;
    }

    File[] fileArr = file.listFiles();

    if (fileArr != null) {
      for (File subFile : fileArr) {
        if (subFile.isDirectory()) {
          deleteFile(subFile.getAbsolutePath());
        }

        if (subFile.isFile()) {
          subFile.delete();
        }
      }
    }

    file.delete();
  }

  // Get the target saving path which not exist, by adding (1), (2), etc
  @NonNull
  public static String getTargetNonExistPath(String path, boolean isDir) {
    return getTargetNonExistFile(path, isDir).getPath();
  }

  // Get the target saving file/dir which not exist, by adding (1), (2), etc
  @NonNull
  public static File getTargetNonExistFile(String path, boolean isDir) {
    if (!isExistFile(path)) {
      return new File(path);
    }
    int index = 1;
    String folder = null;
    String name = null;
    String fileType = null;
    if (!isDir) {
      int slashPos = path.lastIndexOf('/');
      folder = path.substring(0, slashPos + 1);
      String filename = path.substring(slashPos + 1);

      name = filename;
      fileType = "";
      int dotPos = filename.lastIndexOf('.');
      if (dotPos != -1) {
        name = filename.substring(0, dotPos);
        fileType = filename.substring(dotPos);
      }
    }

    while (true) {
      String testingPath =
          isDir ? path + "(" + index + ")" : folder + name + "(" + index + ")" + fileType;
      File node = new File(testingPath);
      if (!node.exists()) {
        return node;
      }
      index += 1;
    }
  }

  // Create a new directory by adding (1) (2), ...
  @NonNull
  public static File createDirByAddSuffix(String path) {
    File f = getTargetNonExistFile(path, true);
    f.mkdirs();
    return f;
  }

  public static void listDir(String path, ArrayList<String> list) {
    File dir = new File(path);
    if (!dir.exists() || dir.isFile()) return;

    File[] listFiles = dir.listFiles();
    if (listFiles == null || listFiles.length <= 0) return;

    if (list == null) return;
    list.clear();
    for (File file : listFiles) {
      list.add(file.getAbsolutePath());
    }
  }

  public static boolean isDirectory(String path) {
    if (!isExistFile(path)) return false;
    return new File(path).isDirectory();
  }

  public static boolean isFile(String path) {
    if (!isExistFile(path)) return false;
    return new File(path).isFile();
  }

  public static long getFileLength(String path) {
    if (!isExistFile(path)) return 0;
    return new File(path).length();
  }

  public static String getExternalStorageDir() {
    return Environment.getExternalStorageDirectory().getAbsolutePath();
  }

  public static String getPackageDataDir(Context context) {
    return context.getExternalFilesDir(null).getAbsolutePath();
  }

  public static String getPublicDir(String type) {
    return Environment.getExternalStoragePublicDirectory(type).getAbsolutePath();
  }

  public static String convertUriToFilePath(final Context context, final Uri uri) {
    String path = null;
    if (DocumentsContract.isDocumentUri(context, uri)) {
      if (isExternalStorageDocument(uri)) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        if ("primary".equalsIgnoreCase(type)) {
          path = Environment.getExternalStorageDirectory() + "/" + split[1];
        }
      } else if (isDownloadsDocument(uri)) {
        final String id = DocumentsContract.getDocumentId(uri);

        if (!TextUtils.isEmpty(id)) {
          if (id.startsWith("raw:")) {
            return id.replaceFirst("raw:", "");
          }
        }

        final Uri contentUri =
            ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

        path = getDataColumn(context, contentUri, null, null);
      } else if (isMediaDocument(uri)) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        Uri contentUri = null;
        if ("image".equals(type)) {
          contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if ("video".equals(type)) {
          contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if ("audio".equals(type)) {
          contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        final String selection = MediaStore.Audio.Media._ID + "=?";
        final String[] selectionArgs = new String[] {split[1]};

        path = getDataColumn(context, contentUri, selection, selectionArgs);
      }
    } else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
      path = getDataColumn(context, uri, null, null);
    } else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
      path = uri.getPath();
    }

    if (path != null) {
      try {
        return URLDecoder.decode(path, "UTF-8");
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  private static String getDataColumn(
      Context context, Uri uri, String selection, String[] selectionArgs) {
    Cursor cursor = null;

    final String column = MediaStore.Images.Media.DATA;
    final String[] projection = {column};

    try {
      cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
      if (cursor != null && cursor.moveToFirst()) {
        final int column_index = cursor.getColumnIndexOrThrow(column);
        return cursor.getString(column_index);
      }
    } catch (Exception e) {

    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return null;
  }

  private static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }

  private static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }

  private static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }
}
