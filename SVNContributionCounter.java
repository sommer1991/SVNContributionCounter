package com.sommer.svnworker;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class SVNContributionCounter {
	
	public static void main(String[] args) {
		SVNContributionCounter counter = new SVNContributionCounter();
		String svnUrl = "https://hp-hp/svn/firstRepo/mavenProject1";
		String username = "harry";
		String password = "harry";
		String diffFile = "D:/different.txt"; //temporary file to save line differences between two revisions, it can be set to arbitrary directory in your disk
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		Date start = null;
		Date end = null;
		try {
			start = format.parse("2016-01-01");
			end = format.parse("2016-01-10");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		List<SVNCommittedResult> resList = counter.getCountResult(svnUrl, username, password, start, end, diffFile);
		for (SVNCommittedResult res : resList) {
			System.out.println("Author " + res.getAuthor() + "has changed " + res.getTotalCount()+ "lines by " + res.getDate());
		}
		
	}
	/*
	 * Count modified lines between adjacent revisions in SVNLogEntry list for a specified period, and sum up the add\delete\total count to each author.
	 */
	public List<SVNCommittedResult> getCountResult(String svnUrl, String username, String password, final Date start,
			final Date end, String diffFile) {
		List<SVNCommittedResult> resultList = new ArrayList<SVNCommittedResult>();
		SVNClientManager manager = getClientManager(username, password);
		List<SVNLogEntry> logList = getLogByDate(manager, svnUrl, start, end);
		Set<String> authorSet = new HashSet<String>();

		for (SVNLogEntry svnLogEntry : logList) {
			authorSet.add(svnLogEntry.getAuthor());
		}

		List<String> authorList = new ArrayList<String>(authorSet);
		
		for (int i = 0; i < authorList.size(); i++) {

			int addSum = 0;
			int deleteSum = 0;
			Date lastCommitTime = null;

			SVNCommittedResult res = new SVNCommittedResult();
			//
			for (int j = 1; j < logList.size(); j++) {
				SVNLogEntry currLog = logList.get(j);
				if (currLog.getAuthor().equals(authorList.get(i))) {
					
					SVNRevision v1;
					if(j==0){ //the last entry in logList should compare with its former revision(not in the specified period)
						v1 = SVNRevision.create(currLog.getRevision()-1);
					}else{
						v1 = SVNRevision.create(logList.get(j - 1).getRevision());
					}
					SVNRevision v2 = SVNRevision.create(currLog.getRevision());
					Map<String, Integer> countMap = getDiffByRevision(manager, svnUrl, v1, v2, diffFile);
					addSum += countMap.get("add");
					deleteSum += countMap.get("delete");
					lastCommitTime = currLog.getDate();
				}
			}
			res.setAddCount(addSum);
			res.setDeleteCount(deleteSum);
			res.setTotalCount(addSum + deleteSum);
			res.setAuthor(authorList.get(i));
			res.setDate(lastCommitTime);
			resultList.add(res);
		}
		return resultList;
	}
	/*
	 * get SVNClientManager to call LogClient and DiffClient
	 */
	private SVNClientManager getClientManager(String username, String password) {

		ISVNOptions options = SVNWCUtil.createDefaultOptions(true);
		SVNClientManager manager = SVNClientManager.newInstance((DefaultSVNOptions) options, username, password);
		return manager;
	}

	/*
	 * get contributor login list  
	 */
	private List<SVNLogEntry> getLogByDate(SVNClientManager manager, String svnUrl, final Date start, final Date end) {
		final List<SVNLogEntry> logList = new ArrayList<SVNLogEntry>();
		SVNLogClient logClient = manager.getLogClient();
		ISVNLogEntryHandler handler = new ISVNLogEntryHandler() {

			public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
				if (logEntry.getDate().after(start) && logEntry.getDate().before(end)) {
					logList.add(logEntry);
				}
			}
		};

		try {
			SVNURL repositoryURL = SVNURL.parseURIEncoded(svnUrl);
			String[] paths = new String[] { "" };
			logClient.doLog(repositoryURL, paths, SVNRevision.HEAD, SVNRevision.create(1), SVNRevision.HEAD, false,
					true, 0, handler);
		} catch (SVNException e) {
			e.printStackTrace();
		}
		return logList;
	}
	/*
	 * get modification count by line between two revisions 
	 */
	private Map<String, Integer> getDiffByRevision(SVNClientManager manager, String svnUrl, SVNRevision v1,
			SVNRevision v2, String diffFile) {
		SVNDiffClient diffClient = manager.getDiffClient();
		Map<String, Integer> countMap = new HashMap<String, Integer>();

		try {
			SVNURL repositoryURL = SVNURL.parseURIEncoded(svnUrl);
			BufferedOutputStream result = new BufferedOutputStream(new FileOutputStream(diffFile));
			diffClient.doDiff(repositoryURL, SVNRevision.HEAD, v1, v2, SVNDepth.INFINITY, true, result);
			result.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (SVNException svne) {
			svne.printStackTrace();
		}

		FileReader fr = null;
		int addCount = 0;
		int deleteCount = 0;
		try {
			fr = new FileReader(diffFile);
			BufferedReader br = new BufferedReader(fr);
			String s = br.readLine();
			while (s != null) {
				if (s.startsWith("+") && !s.startsWith("+++")) {
					addCount++;
				} else if (s.startsWith("-") && !s.startsWith("---")) {
					deleteCount++;
				}

				s = br.readLine();
			}
			br.close();
			fr.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		countMap.put("add", addCount);
		countMap.put("delete", deleteCount);
		return countMap;
	}

}

class SVNCommittedResult {

	String author;
	Date date;
	int addCount;
	int deleteCount;
	int totalCount;

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public int getAddCount() {
		return addCount;
	}

	public void setAddCount(int addCount) {
		this.addCount = addCount;
	}

	public int getDeleteCount() {
		return deleteCount;
	}

	public void setDeleteCount(int deleteCount) {
		this.deleteCount = deleteCount;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

}