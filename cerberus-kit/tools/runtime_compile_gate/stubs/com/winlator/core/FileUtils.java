package com.winlator.core;
import java.io.File; import android.content.Context;
public class FileUtils {
 public static boolean delete(File f){return true;} public static boolean copy(File a,File b){return true;} public static boolean copy(File a,File b,Callback<File> c){return true;} public static void copy(Context c,String s,File f){}
 public static boolean chmod(File f,int m){return true;} public static String readString(File f){return "";} public static String readString(Context c,String s){return "";} public static boolean writeString(File f,String s){return true;}
 public static boolean isSymlink(File f){return false;} public static String readSymlink(File f){return "";} public static void symlink(String t,String l){}
}
