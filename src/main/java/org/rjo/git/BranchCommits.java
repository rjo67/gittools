package org.rjo.git;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Simple snippet which shows how to show diffs between branches
 *
 * @author dominik.stadler at gmx.at
 */
public class BranchCommits {

   private final static long SECONDS_IN_WEEK = 60 * 60 * 24 * 7;
   // per default, don't search back longer than this number of weeks for a base commit
   private final static long DEFAULT_CUTOFF_IN_WEEKS = 8;
   private long cutoffInSeconds;
   private String ref1;
   private String ref2;
   private File gitDir;

   public BranchCommits(String[] args) throws IllegalArgumentException {
      parseArgs(args);
   }

   private void parseArgs(String[] args) throws IllegalArgumentException {
      // TODO Auto-generated method stub

      setCutoff(DEFAULT_CUTOFF_IN_WEEKS);
      this.ref1 = "refs/heads/testbranch";
      this.ref2 = "refs/heads/master";
      this.gitDir = new File("c:/Users/rich/git/test/.git");
   }

   private void setCutoff(long cutoffInWeeks) {
      this.cutoffInSeconds = cutoffInWeeks * SECONDS_IN_WEEK;
   }

   private void run() throws IOException {
      try (Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().build()) {

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
            System.out.println(String.format("Shared commit: %.7s", sharedId));
            int commitCount = countCommits(commitsOnBranch, sharedId);
            showCount(commitCount, sharedId, ref1);
            commitCount = countCommits(commitsOnBranch2, sharedId);
            showCount(commitCount, sharedId, ref2);
         }
      }
   }

   private static void showCount(int commitCount, String sharedId, String ref) {
      if (commitCount == 1) {
         System.out.println(String.format("%d commit %.7s...HEAD for %s", commitCount, sharedId, ref));
      } else {
         System.out.println(String.format("%d commits %.7s...HEAD for %s", commitCount, sharedId, ref));
      }

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
      new BranchCommits(args).run();
   }
}