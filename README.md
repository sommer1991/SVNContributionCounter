# SVNContributionCounter
count SVN contribution by lines of code for each author in a specified time period.  

Usage:  
specify the following variables in the main method:  
1. SVN repository, username and password.  
2. Temporary file to save line differences between two revisions. It can be set to arbitrary directory in your disk.  
3. Time period.  

Example:  
svnUrl = "https://hp-hp/svn/firstRepo/mavenProject1";  
username = "harry";  
password = "harry";  
diffFile = "D:/different.txt";   
start = format.parse("2016-01-01");  
end = format.parse("2016-01-10");  
 
