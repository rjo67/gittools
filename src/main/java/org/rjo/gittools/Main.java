package org.rjo.gittools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Main {

   /**
    * Helper class to call the available commands.
    *
    * @param args
    *           first arg must be the name of the "subcommand", all other args are given to this program.
    */
   public static void main(String[] args) {

      String command = args[0];

      try {
         Class<?> cl = Class.forName("org.rjo.gittools." + command);
         Method method = cl.getMethod("main", String[].class);
         String[] newargs = Arrays.copyOfRange(args, 1, args.length);
         // note: cast to Object is vital!
         method.invoke(null, (Object) newargs);
      } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e) {
         System.out.println("unrecognised command: " + command);
         e.printStackTrace();
      }
   }
}
