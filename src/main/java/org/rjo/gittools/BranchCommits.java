package org.rjo.gittools;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * This program analyses two git branches and displays their base commit (i.e. where the branch was created)
 * and how many commits since then have been made on each branch.
 *
 * @author rjo67
 */
public class BranchCommits {

   private final static long SECONDS_IN_WEEK = 60 * 60 * 24 * 7;

   // per default, don't search back longer than this number of weeks for a base commit
   private final static String DEFAULT_CUTOFF_IN_WEEKS = "8";

   private long cutoffInSeconds;
   private String ref1;
   private String ref2;
   private File gitDir;

   private Options options;
   private String[] args;

   public BranchCommits(String[] args) {
      Option ref1 = Option.builder("r1").longOpt("ref1").desc("first ref (optional, default='refs/heads/master')")
            .hasArg().argName("ref1").build();
      Option ref2 = Option.builder("r2").longOpt("ref2").desc("second ref").hasArg().argName("ref2").required().build();
      Option gitdir = Option.builder("d").longOpt("dir")
            .desc(".git directory (optional, will be searched for if not set)").hasArg().argName("gitdir").build();
      Option cutoff = Option.builder("c").longOpt("cutoff")
            .desc("cutoff for the commit search (in weeks) (optional, default=" + DEFAULT_CUTOFF_IN_WEEKS + ")")
            .hasArg().argName("cutoff").type(Integer.class).build();

      options = new Options();
      options.addOption(ref1);
      options.addOption(ref2);
      options.addOption(cutoff);
      options.addOption(gitdir);
      options.addOption("h", "help", false, "displays help");
      options.addOption("s", "short", false, "short output");

      this.args = args;
   }

   private void setCutoff(long cutoffInWeeks) {
      this.cutoffInSeconds = cutoffInWeeks * SECONDS_IN_WEEK;
   }

   private void run() throws IOException, ParseException {
      CommandLine cmdLine = new DefaultParser().parse(options, args);
      if (cmdLine.hasOption("h")) {
         HelpFormatter formatter = new HelpFormatter();
         formatter.printHelp(90, "BranchCommits",
               "Displays the common commit and the resulting number of commits for two given refs", options,
               "\nrjo67/2015", true);
         return;
      }
      setCutoff(Integer.parseInt(cmdLine.getOptionValue("c", DEFAULT_CUTOFF_IN_WEEKS)));
      ref1 = cmdLine.getOptionValue("r1", "refs/heads/master");
      if (cmdLine.hasOption("r2")) {
         ref2 = cmdLine.getOptionValue("r2");
         // "refs/heads/testbranch2";
      } else {
         throw new ParseException("Value for --ref2 is missing");
      }
      if (cmdLine.hasOption("d")) {
         this.gitDir = new File(cmdLine.getOptionValue("d"));
      } else {
         this.gitDir = findGitDir();
      }

      try (Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().build()) {

         // check refs
         if (repository.getRef(ref1) == null) {
            throw new ParseException("unknown ref1: " + ref1);
         }
         if (repository.getRef(ref2) == null) {
            throw new ParseException("unknown ref2: " + ref2);
         }

         // the set is used for quick lookup
         Set<String> commitIdsOnBranch = new HashSet<>(50);
         List<RevCommit> commitsOnBranch = new ArrayList<>(50);
         Ref head = repository.getRef(ref1);
         try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            walk.markStart(commit);
            Iterator<RevCommit> commits = walk.iterator();
            while (commits.hasNext()) {
               commit = commits.next();
               String id = commit.getName();
               commitIdsOnBranch.add(id);
               commitsOnBranch.add(commit);
            }
         }

         String sharedId = null;
         long currentUnixTimeStamp = Instant.now().getEpochSecond();

         head = repository.getRef(ref2);
         List<RevCommit> commitsOnBranch2 = new ArrayList<>(50);
         try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            walk.markStart(commit);
            Iterator<RevCommit> commits = walk.iterator();
            while (commits.hasNext()) {
               commit = commits.next();
               String id = commit.getName();
               commitsOnBranch2.add(commit);
               if (commitIdsOnBranch.contains(id)) {
                  sharedId = id;
                  break;
               }
               if (commit.getCommitTime() < (currentUnixTimeStamp - cutoffInSeconds)) {
                  System.out.println("search reached cutoff time, last commit: "
                        + Instant.ofEpochSecond(commit.getCommitTime()));
                  break;
               }
            }
         }

         if (sharedId == null) {
            System.out.println(String.format("no shared commit between %s and %s", ref1, ref2));
         } else {
            int count1 = countCommits(commitsOnBranch, sharedId);
            int count2 = countCommits(commitsOnBranch2, sharedId);
            if (cmdLine.hasOption("s")) {
               System.out.println(String.format("%.7s+%d+%d", sharedId, count1, count2));
            } else {
               showCount(sharedId, count1, ref1, count2, ref2);
            }
         }
      }
   }

   /**
    * Find the location of the .git directory, starting from the current directory.
    *
    * @throws ParseException
    *            if no directory could be found
    */
   private File findGitDir() throws ParseException {
      File file = new File(System.getProperty("user.dir"));
      while (file != null) {
         if (new File(file, ".git").isDirectory()) {
            return new File(file, ".git");
         }
         file = file.getParentFile();
      }
      throw new ParseException("Cound not find .git directory");
   }

   private static void showCount(String sharedId, int commitCount1, String ref1, int commitCount2, String ref2) {
      System.out.println(String.format("       %d commit%s (%s)", commitCount1, commitCount1 == 1 ? "" : "s", ref1));
      System.out.println("      /");
      System.out.println(String.format("%.7s", sharedId));
      System.out.println("      \\");
      System.out.println(String.format("       %d commit%s (%s)", commitCount2, commitCount2 == 1 ? "" : "s", ref2));
   }

   /**
    * returns the number of commits in 'commitsOnBranch' upto but not including 'sharedID'
    */
   private static int countCommits(List<RevCommit> commitsOnBranch, String sharedId) {
      int commitCount = 0;
      for (RevCommit commit : commitsOnBranch) {
         if (commit.name().equals(sharedId)) {
            break;
         }
         commitCount++;
      }
      return commitCount;
   }

   public static void main(String[] args) throws IOException {
      try {
         new BranchCommits(args).run();
      } catch (ParseException e) {
         System.err.println(e.getMessage());
      }
   }
}