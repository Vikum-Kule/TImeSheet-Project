@Grab(group='org.apache.poi', module='poi', version='5.2.5')
@Grab(group='org.apache.poi', module='poi-ooxml', version='5.2.5' )
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import java.security.SecureRandom;
import java.Utils.*
import groovy.json.JsonOutput;
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFDataFormat

import org.json.JSONArray;
import org.json.JSONObject;
import hudson.FilePath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;



import java.io.File
/*===================== External method loads =================== */
def svnHelper
def pipelineHelper
//credentials
tempoAccessCred = 'TEMPO_ACCESS_TOKEN'
xeroAccessCred = 'XERO_ACCESS_TOKEN'
jiraAccessCred = 'JIRA_ACCESS_TOKEN'
String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'
String tempoCode = 'TEMPO_CODE'
String jiraClientId = 'JIRA_CLIENT_ID'
String jiraClientSecret = 'JIRA_CLIENT_SECRET'
String jiraCode = 'JIRA_CODE' 
String xeroClientId = 'XERO_CLIENT_ID'
String xeroClientSecret = 'XERO_CLIENT_SECRET'
String xeroTenantId = 'XERO_TENANT_ID'
Boolean isFinalInvoice = params.Mark_Worklogs_As_Invoiced
Boolean isInvoiceInExcel = (params.Invoices_Create_On == "SPREADSHEET") ? true : false
String finalDate = (params.FINAL_DATE == null) ? "" : params.FINAL_DATE

def accounts = (params.Accounts == null) ? "" : params.Accounts
def invoiceParams = (params.Invoice_Number == null) ? "" : params.Invoice_Number
String customer = params.Customer

//tokens
tempoAccessToken = null
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
jiraAccessToken = null
jiraRefreshToken = 'JIRA_REFRESH_TOKEN' 
xeroAccessToken = null
xeroRefreshToken = 'XERO_REFRESH_TOKEN'

Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
String jobExecutionNode = 'master'
teamResponse = null
mainJSON = null
accountKeyList = []
def tempoRoles = null

def quoteList
workLogList = []
invoiceNumberList = []
String tempoRefreshBody
String jiraRefreshBody
def xeroRefreshBody

def invoicePrintjson

svnCredentialsId = "customer-repo-svn-credentials"

def checkoutSVN(localDir, remotePath, svnCredentialsId) {
	// Make directory if not exist.
	if (isUnix()) {
		sh returnStdout: true, script: "mkdir -p ${localDir}"
	} else {
		bat returnStdout: true, script: "if not exist \"${localDir}\" mkdir \"${localDir}\""
	}
	def svnCommandLine = "co ${remotePath} ${localDir} --depth infinity"
	execSVN(svnCommandLine, svnCredentialsId)
}

// Main svn method. Jenkins Credentials Id should be provided.
def execSVN(svnCommandLine, svnCredentialsId) {
    withCredentials([usernamePassword(
			credentialsId: svnCredentialsId, 
			passwordVariable: 'SVN_PASSWORD', 
			usernameVariable: 'SVN_USERNAME')]) {
        def svnArgs = "--username \"$SVN_USERNAME\" --password \"$SVN_PASSWORD\" --no-auth-cache"
        if (isUnix()) {
			sh returnStdout: true, script: "svn $svnArgs $svnCommandLine"
		} else {
			bat returnStdout: true, script: "svn $svnArgs $svnCommandLine"
		}
    }
}

def sendGetRequest(url, header, platform, refreshTokenPayload) {
    def response = null
     withCredentials([
            string(credentialsId: tempoAccessCred, variable: 'tokenTempo'),
            string(credentialsId: xeroAccessCred, variable: 'tokenXero'),
            string(credentialsId: jiraAccessCred, variable: 'tokenJira'),
      ]){

        if(platform == "TEMPO"){
          header.each { data ->
                  if (data.name == "Authorization") {
                      data.value = "Bearer ${tokenTempo}"
                  }
              }
        }else if(platform == "JIRA"){
          header.each { data ->
                  if (data.name == "Authorization") {
                      data.value = "Bearer ${tokenJira}"
                  }
              }
        }else if(platform == "XERO"){
          header.each { data ->
                  if (data.name == "Authorization") {
                      data.value = "Bearer ${tokenXero}"
                  }
              }
        }else{

        }

        println("Request Header : ${header}")

        response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'GET',
                               validResponseCodes: '200, 401,403, 404')
        // Check if the request was successful or not
        if (response.status == 401){
          if (platform == "TEMPO"){
              refreshTokens(platform, refreshTokenPayload)
              sendGetRequest(url, header, platform, refreshTokenPayload)
          }else if (platform == "JIRA"){
              refreshTokens(platform, refreshTokenPayload)
              sendGetRequest(url, header, platform, refreshTokenPayload)

          }else if(platform == "XERO"){
              refreshTokens(platform, refreshTokenPayload)
              sendGetRequest(url, header, platform, refreshTokenPayload)

          }
        }else{
          println(response)
          return response
        }
    }
}


def sendPostRequest( url, payload, header, platform, refreshTokenPayload) { 
  def response = null
     withCredentials([
            string(credentialsId: tempoAccessCred, variable: 'tokenTempo'),
            string(credentialsId: xeroAccessCred, variable: 'tokenXero'),
            string(credentialsId: jiraAccessCred, variable: 'tokenJira'),
    ]){
      if(platform == "TEMPO"){
          header.each { data ->
            if (data.name == "Authorization") {
                data.value = "Bearer ${tokenTempo}"
            }
          }
      }else if(platform == "XERO"){
            header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tokenXero}"
              }
            }
      }
      println("header: ${header}")
      response = httpRequest acceptType: 'APPLICATION_JSON',
                 contentType: 'APPLICATION_JSON',
                 customHeaders: header,
                 httpMode: 'POST',
                 requestBody: payload,
                 consoleLogResponseBody: true,
                 url: url
      if (response.status == 401){
        if(platform == "XERO"){
            refreshTokens(platform, refreshTokenPayload)
            sendPostRequest( url, payload, header, platform, refreshTokenPayload)
        }else if (platform == "TEMPO"){
            refreshTokens(platform, refreshTokenPayload)
            sendPostRequest( url, payload, header, platform, refreshTokenPayload)
        }
      }else{
        println(response)
        return response
      }
    
    } 
}
def refreshTokens(platform, refreshTokenPayload){
    switch (platform) {
      case "TEMPO":
          println "Tempo Refresh Token Update"
          String tempoRefreshUrl = 'https://api.tempo.io/oauth/token/?grant_type=&client_id=&client_secret=&refresh_token'
          def tempoHeader = [[
                                  name: "Content-Typen",
                                  value: "application/x-www-form-urlencoded"
                              ]]

          def tempoTokenPayload = refreshTokensRequest(tempoRefreshUrl, refreshTokenPayload, tempoHeader)
          // save on disk
          def jsonResponse = readJSON text: tempoTokenPayload.content
          tempoAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, tempoRefreshToken)
          updateTokens(tempoAccessToken, tempoAccessCred)
          break
      case "JIRA":
          println "Jira Refresh Token Update"
          String tempoRefreshUrl = 'https://auth.atlassian.com/oauth/token'
          def jiraHeader = [[
                                  name: "Content-Typen",
                                  value: "application/x-www-form-urlencoded"
                              ]]
          def jiraTokenPayload = refreshTokensRequest(tempoRefreshUrl, refreshTokenPayload, jiraHeader)
          // save on disk
          def jsonResponse = readJSON text: jiraTokenPayload.content
          jiraAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, jiraRefreshToken)
          updateTokens(jiraAccessToken, jiraAccessCred)
          break
      case "XERO":
          println "Xero Refresh Token Update"
          String xeroRefreshUrl = 'https://identity.xero.com/connect/token'
          def xeroHeader = [[name: "Content-Typen",value: 'application/x-www-form-urlencoded']]
          def xeroTokenPayload = refreshTokensRequest(xeroRefreshUrl, refreshTokenPayload, xeroHeader )
          // save on disk
          def jsonResponse = readJSON text: xeroTokenPayload.content
          xeroAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, xeroRefreshToken)
          updateTokens(xeroAccessToken, xeroAccessCred)
          break
      default:
          println "No match found."
  }
}

def refreshTokensRequest (url, payload, header ){
  println("payload: ${payload}  header: ${header}")
  def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: "APPLICATION_FORM",
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    validResponseCodes: '200, 401, 404',
                    consoleLogResponseBody: true,
                    url: url
    
    println(response)
    return response

}

def updateTokens(refreshToken, credentialId) {

  // Specify the new secret text
  def newSecretText = refreshToken

  // Access the Jenkins instance
  def jenkins = Jenkins.getInstance()

  // Access the global domain
  def globalDomain = Domain.global()

  // Find the existing credential by ID
  def existingCredential = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0]
      .getStore()
      .getCredentials(globalDomain)

  def credentialToUpdate = existingCredential.find { it.id == credentialId && it instanceof StringCredentials }

  if (credentialToUpdate) {
      // Update the credential with new secret text
      CredentialsStore store = Jenkins.getInstance().getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
      StringCredentials newCredential = new StringCredentialsImpl(credentialToUpdate.scope, credentialToUpdate.id, credentialToUpdate.description, Secret.fromString(newSecretText))
      
      // Remove the old credential
      store.removeCredentials(globalDomain, credentialToUpdate)
      
      // Add the updated credential
      store.addCredentials(globalDomain, newCredential)

      println("Credential updated successfully.")
  } else {
      println("Credential with ID ${tempoRefreshToken} not found.")
  }
}

def organizeUserDetails(userId, refreshBody, refreshToken){
  String userBaseUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/user?accountId="
   //update actor
   def jiraActorRequestHeaders = [[
           name: "Authorization",
           value: "Bearer ${jiraAccessToken}"
       ]]
   def userResponse = sendGetRequest("${userBaseUrl}${userId}", jiraActorRequestHeaders, "JIRA", "${refreshBody}${refreshToken}" )

   def userJson  = readJSON(text: userResponse.content)
   return userJson
}

// def writeResponseToCSV(mainJSON) {
//     def csvContentForValidWorklogs = new StringBuilder()
//     def csvContentForInvalidWorklogs = new StringBuilder()
    
//     // Add title to CSV content
//     csvContentForValidWorklogs.append("WORK LOG ID, ISSUE ID, BILLABLE SECONDS,START DATE, DESCRIPTION, CREATED AT, UPDATED AT, STATUS, REVIEWER, USER, ACCOUNT KEY, TEAM \n")
//     csvContentForInvalidWorklogs.append("WORK LOG ID, ISSUE ID, BILLABLE SECONDS,START DATE, DESCRIPTION, CREATED AT, UPDATED AT,STATUS, REVIEWER, USER, ACCOUNT KEY, TEAM, ERROR \n")

//     // Iterate over results and append to CSV content
//     mainJSON.teams.each { team ->
//         team.timesheets?.each { timesheet ->
//           timesheet.worklogs?.each{worklog ->
//             def attributeVal = '-'
//             worklog.attributes.values?.each{attribute ->
//               if(attribute.key == '_TempoAccount_'){
//                 attributeVal = attribute.value 
//               }
//             }
//               if(!worklog.errorLogs){
//                 csvContentForValidWorklogs.append("${worklog.tempoWorklogId},${worklog.issue.key},${worklog.billableSeconds},${worklog.startDate},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${timesheet.user.displayName},${attributeVal},${team.name}\n")
//               }
//               else{
//                 csvContentForInvalidWorklogs.append("${worklog.tempoWorklogId},${worklog.issue.key},${worklog.billableSeconds},${worklog.startDate},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${timesheet.user.displayName},${attributeVal},${team.name},${worklog.errorLogs}\n")
//               }
                
//           }         
//         }
//     }
//     // Write content to CSV file
//     writeFile file: "timeSheet.csv", text: csvContentForValidWorklogs.toString()
//     writeFile file: "audit.csv", text: csvContentForInvalidWorklogs.toString()
//     archiveArtifacts 'timeSheet.csv'
//     archiveArtifacts 'audit.csv'
// }

node('windows') {
  try {
      
    def localFile
    
    if (getContext(hudson.FilePath)) {
            println "Creating Hudson Workspace"
            localFile = getContext(hudson.FilePath)
            println localFile.list()
    }
    
    stage('Preparation') {
      cleanWs()
      withCredentials([
            string(credentialsId: tempoClientId, variable: 'clientIdTempo'),
            string(credentialsId: tempoClientSecret, variable: 'clientSecretTempo'),
            string(credentialsId: jiraClientId, variable: 'clientIdJira'),
            string(credentialsId: jiraClientSecret, variable: 'clientSecretJira'),
            string(credentialsId: xeroClientId, variable: 'clientIdXero'),
            string(credentialsId: xeroClientSecret, variable: 'clientSecretXero')
      ]){
        //prepare payload for refresh tokens
        tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
        jiraRefreshBody = "grant_type=refresh_token&client_id=${clientIdJira}&client_secret=${clientSecretJira}&redirect_uri=https://enactor.co/&refresh_token="
        xeroRefreshBody = "grant_type=refresh_token&client_id=${clientIdXero}&client_secret=${clientSecretXero}&refresh_token="
      }

      if(invoiceParams != "" && isFinalInvoice){
        invoicePramsList =  invoiceParams.split(',');

        def pattern = /\((.*?)\)[^(]*$/
        
        invoicePramsList.each{item ->
          def splittedItem = item.split(':');
          def matcher = (splittedItem[0] =~ pattern)
          if (matcher.find()) {
              def selectedAccountKey = matcher.group(1)
              def trimmedItem = splittedItem[1].trim()
              if(trimmedItem != ""){
                invoiceNumberList.add(selectedAccountKey + ":" +trimmedItem)
              }else{
                currentBuild.result = 'ABORTED'
                error('Invoice Number Not Exist')
              }
          }
          
        }
      }

      if(accounts != ""){
          accountsList = accounts.split(',');
          def pattern = /\((.*?)\)[^(]*$/
          
          accountsList.each{ acc->
            def matcher = (acc =~ pattern)
            if (matcher.find()) {
                def selectedAccountKey = matcher.group(1)
                accountKeyList.add(selectedAccountKey)
            }
          }

      }else{
        currentBuild.result = 'ABORTED'
        error('Select At Least One Account from Account List')
      }
    }

    stage("Checkout Template Files") {
        
      bat('dir')
       
        // Checking out the branch
      def svnBranch = "https://dev.enactor.co.uk/svn/dev/Resources/Jenkins/Groovy/TimesheetIntegration"
      echo "Checking out branch ${svnBranch}"
      checkoutSVN("TimesheetIntegration/", svnBranch, svnCredentialsId)
	  }
    
    stage('GetAllTeams'){
      String fetchTeamUrl = "https://api.tempo.io/4/teams?limit=50&offset=0"
      withCredentials([
            string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
      ]){
        def isNextAvailable = true;
        
        while(isNextAvailable){
          def requestHeaders = [[
                                  name: "Authorization",
                                  value: "Bearer ${tempoAccessToken}"
                              ]]
          teamResponse = sendGetRequest(fetchTeamUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
          if(teamResponse.status == 200){
            //append fetch teams to existing teams
            def teamJSON = readJSON(text: teamResponse.content)
            if(mainJSON == null){
              mainJSON = teamJSON
              //rename feilds
              mainJSON.teams = mainJSON.results
              mainJSON.remove('results')
            }else{
              mainJSON.teams.addAll(teamJSON.results)
            }

            // if available next url
            if(teamJSON.metadata.next){
              fetchTeamUrl = teamJSON.metadata.next
            }else{
              isNextAvailable= false
            } 
          }
          else{
           isNextAvailable= false 
          }
        }
      }
    }

    stage('FetchTimeSheets'){
      if(!mainJSON.teams.isEmpty()){
          withCredentials([
              string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo'),
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ]){
                //remove properties that are not needed
                mainJSON.remove('self')
                mainJSON.remove('metadata')

                mainJSON.teams.each { team ->
                  def teamId = team.id
                  def teamName = team.name

                   //fetched projectId and projectNames
                  println "Team ID: $teamId, team Name: $teamName"

                  //define timesheet feild
                  team.timesheets = []

                  // Calculate last Monday
                  Calendar cal = Calendar.getInstance()
                  //calculate last monday of the week
                  int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                  int daysToLastMonday = Calendar.MONDAY - dayOfWeek
                  
                  if (daysToLastMonday == 0) {
                      // Last week Monday
                      daysToLastMonday = -7 
                  }
                  
                  cal.add(Calendar.DAY_OF_YEAR, daysToLastMonday)
                  Date lastMonday = cal.getTime()
                  
                  cal.add(Calendar.DAY_OF_YEAR, 6)
                  Date lastSunday = cal.getTime()

                  // Format dates
                  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
                  
                  //run loop to fetch last 8weeks(2months) timesheets
                  String startDate
                  String endDate
                  for(int i = 0; i < 24; i++) {
                    if(i==0){
                      startDate = sdf.format(lastMonday)
                      endDate = sdf.format(lastSunday)
                    }else{
                      // Now, calculate the previous Monday and Sunday based on lastMonday
                      cal.setTime(lastMonday) // Set calendar to last Monday
                      cal.add(Calendar.DAY_OF_YEAR, -7) // Go back one more week for previous Monday
                      Date previousMonday = cal.getTime()
                      cal.add(Calendar.DAY_OF_YEAR, 6) // Add 6 days to get to previous Sunday
                      Date previousSunday = cal.getTime()

                      startDate = sdf.format(previousMonday)
                      endDate = sdf.format(previousSunday)
                      lastMonday = previousMonday

                    }
      
                    String fetchTimeSheetUrl = "https://api.tempo.io/4/timesheet-approvals/team/${teamId}?from=${startDate}&to=${endDate}"
                    def requestHeaders = [[
                                        name: "Authorization",
                                        value: "Bearer ${tempoAccessToken}"
                                    ]]
                    def timeSheetResponse = sendGetRequest(fetchTimeSheetUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )

                    if(timeSheetResponse){
                        if(timeSheetResponse.status == 200){
                          def timeSheetjson  = readJSON(text: timeSheetResponse.content)
                          //filter Approved Timesheets
                          timeSheetjson.results = timeSheetjson.results.findAll { timesheet ->
                                                    timesheet.status?.key == 'APPROVED'
                                                  }
                          if(!timeSheetjson.results.isEmpty()){
                            team.timesheets.addAll(timeSheetjson.results)
                          }
                        }else{
                        }
                    } 

                  }
                }          
                
            }
      }     
    }

     stage('FetchUsers'){
      if(!mainJSON.teams.isEmpty()){
        withCredentials([
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ])
        {
          mainJSON.teams.each { team ->
                team.timesheets?.each { timesheet ->
                  //fetch actor details
                  def actorJSON= organizeUserDetails(timesheet.status.actor.accountId, jiraRefreshBody, refreshTokenJira)
                  timesheet.status.actor = actorJSON

                  //fetch user details
                  def userJSON = organizeUserDetails(timesheet.user.accountId, jiraRefreshBody, refreshTokenJira)
                  timesheet.user = userJSON

                  //fetch reviewer details
                  def reviewerJSON = organizeUserDetails(timesheet.reviewer.accountId, jiraRefreshBody, refreshTokenJira)
                  timesheet.reviewer = reviewerJSON
                }
          }
  
      }
      }
    }

    stage('FetchWorklogs'){
      if(!mainJSON.teams.isEmpty()){
        withCredentials([
              string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
            ])
        {
          // Define the date formatter
        Date lastDayOfLastMonth = null
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
        if(finalDate == ""){

          // Calculate the date range for the last month
          Calendar cal = Calendar.getInstance()
          cal.add(Calendar.MONTH, -1)
          cal.set(Calendar.DAY_OF_MONTH, 1)
          Date firstDayOfLastMonth = cal.getTime()

          cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
          lastDayOfLastMonth = cal.getTime()
        }else{
          // Convert the string to a Date object
          lastDayOfLastMonth = sdf.parse(finalDate)
        }

        println("Final Date : ${lastDayOfLastMonth}")

          mainJSON.teams.each { team ->
                team.timesheets?.each { timesheet ->
                  String workLogUrl = timesheet.worklogs.self

                  def requestHeaders = [[
                                      name: "Authorization",
                                      value: "Bearer ${tempoAccessToken}"
                                  ]]
                  def worLogResponse = sendGetRequest(workLogUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )
                  if(worLogResponse){
                      if(worLogResponse.status == 200){
                        def workLogjson  = readJSON(text: worLogResponse.content)
                        //filetr worklogs within yesterday
                        workLogjson.results = workLogjson.results.findAll{ workLog ->
                              Date worklogDate = sdf.parse(workLog.startDate)
                              boolean isAccountExist = false
                              boolean isInvoiceExist = false
                              workLog.attributes.values?.each{attribute ->
                                String val = attribute.value
                                if(attribute.key == '_TempoAccount_' && val != 'null'){
                                  accountKeyList.each{key->
                                    if(key == val){
                                        isAccountExist = true
                                    }
                                  }
                                }
                                if(attribute.key == '_InvoiceNo(DONOTEDIT)_' && val != 'null'){
                                  isInvoiceExist = true
                                }
                              }
                              !worklogDate.after(lastDayOfLastMonth) && (isAccountExist && !isInvoiceExist)
                              }
                        timesheet.worklogs = workLogjson.results
                      }else{
                      }
                  }
                  
                }
                if(team.timesheets){
                  team.timesheets = team.timesheets.findAll{ timesheet ->
                    !timesheet.worklogs.isEmpty()
                  }
                }
          }

          println("remove duplicate worklogs")
          // Set to  track  worklog IDs
          Set worklogIdSet = new HashSet()
          mainJSON.teams.each { team ->
              team.timesheets.each { timesheet ->
                  def uniqueWorklogs = []
                  timesheet.worklogs.each { worklog ->
                      if (worklogIdSet.add(worklog.tempoWorklogId)) {
                          uniqueWorklogs.add(worklog)
                      }
                  }
                  // Update timesheet with unique worklogs
                  timesheet.worklogs = uniqueWorklogs
              }
          }
          
      }
      }
    }

    stage('Get Accounts'){
       if(!mainJSON.teams.isEmpty()){
         withCredentials([
               string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
             ])
         {
           mainJSON.teams.each { team ->
                 team.timesheets?.each { timesheet ->
                   timesheet.worklogs?.each{worklog ->
                     worklog.attributes.values?.each{attribute ->
                       String val = attribute.value
                       if(attribute.key == '_TempoAccount_' && val != 'null'){
                        //find invoice number according to account key
                          invoiceNumberList.each{item ->
                              def splittedList = item.split(':');
                              def key = splittedList[0]
                              def inNumber = splittedList[1]

                              if(key == val){
                                worklog.invoiceNumber = inNumber
                              }
                          }

                         //fetch account according to the account key
                               def accountBody = '''
                                               {
                                                 "keys": [],
                                                 "statuses": [
                                                   "OPEN"
                                                 ]
                                               }
                                             '''
                           def accountJson  = readJSON(text: accountBody)
                           accountJson.keys.add(attribute.value)
                           def finalAccount = JsonOutput.prettyPrint(accountJson.toString())
                           String accountUrl = "https://api.tempo.io/4/accounts/search"
                          
                           def requestHeaders = [[
                                               name: "Authorization",
                                               value: "Bearer ${tempoAccessToken}"
                                           ]]
                           def accountResponse = sendPostRequest( accountUrl, finalAccount, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")
                           if(accountResponse.status == 200){
                               accountResponseJSON  = readJSON(text: accountResponse.content)
                               worklog.account = accountResponseJSON.results[0]
                           }
                       }
                     }
                   }
                 }
           }

         }
       }
    }

    stage('FetchJiraTickets'){
       if(!mainJSON.teams.isEmpty()){
         withCredentials([
               string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
             ])
         {
           mainJSON.teams.each { team ->
                 team.timesheets?.each { timesheet ->
                   timesheet.worklogs?.each{worklog ->
                     def issueUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/issue/${worklog.issue.id}"

                     def jiraRequestHeaders = [[
                             name: "Authorization",
                             value: "Bearer ${jiraAccessToken}"
                         ]]
                     def issueResponse = sendGetRequest(issueUrl, jiraRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}" )
                     if(issueResponse.status == 200){
                       def issueJson  = readJSON(text: issueResponse.content)
                       worklog.issue.key = issueJson.key
                       worklog.issue.summery = issueJson.fields.summary
                     }
                   }
                 } 
           }
             def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
         }
       }
    }

    stage('Fetch Tempo Roles'){
       withCredentials([
               string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
             ])
         {
           String roleUrl = "https://api.tempo.io/4/roles"

           def requestHeaders = [[
                                    name: "Authorization",
                                    value: "Bearer ${tempoAccessToken}"
                                ]]
           def roleResponse = sendGetRequest(roleUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")
           if(roleResponse.status == 200){
               roleJSON = readJSON(text: roleResponse.content)
               tempoRoles = roleJSON.results
           }
         }
    }

    stage('Fetch Quotes'){
      withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero'),
              string(credentialsId: xeroTenantId, variable: 'tenantIdXero')
            ])
        {
          accountKeyList.each{key ->
            String fetchQuoteUrl = "https://api.xero.com/api.xro/2.0/Quotes?QuoteNumber=${key}"

            def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                  [name: "xero-tenant-id", value: "${tenantIdXero}"],
                                ]
            def quoteResponse = sendGetRequest(fetchQuoteUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
            if(quoteResponse.status == 200){
                def quoteJSON = readJSON(text: quoteResponse.content)
                if(quoteList == null){
                  quoteList = quoteJSON
                }else{
                  quoteList.Quotes.add(quoteJSON.Quotes[0])
                }
            }
          }
          if(quoteList == null){
            currentBuild.result = 'ABORTED'
            error('Something Went Wrong When Fecthing Quotes or Quotes Are Not Exist For Selected Accounts ')
          }
        }
    }

    stage('Fetch Cost Tracker Projects'){
       if(!mainJSON.teams.isEmpty()){
         withCredentials([
               string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
             ])
         {
           //fecth cost tracking projects
           String costProjectsUrl = "https://api.tempo.io/cost-tracker/1/projects"
           def requestHeaders = [[
                                  name: "Authorization",
                                  value: "Bearer ${tempoAccessToken}"
                                 ]]
           costProjectResponse = sendGetRequest(costProjectsUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
           if(costProjectResponse.status == 200){
             def costJSON = readJSON(text: costProjectResponse.content)

             //assign costs for worklogs
             mainJSON.teams.each { team ->
                 team.timesheets?.each { timesheet ->
                   timesheet.worklogs?.each{worklog ->
                     def costProject = costJSON.results.findAll{project->
                                project.name == "${worklog.account.key} : ${worklog.account.name} - Cost"
                           }
                           if(!costProject.isEmpty()){
                             //fetch rates
                             String roleUrl = costProject[0].roles.self
                             def rateRequestHeaders = [[
                                                   name: "Authorization",
                                                   value: "Bearer ${tempoAccessToken}"
                                                   ]]

                             roleResponse = sendGetRequest(roleUrl, rateRequestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
                             if(roleResponse.status == 200){
                               def roleJSON = readJSON(text: roleResponse.content) 
                               roleJSON.roles.each{ role->
                                println("Cost project User: ${role.teamMember.userLink.linked.originId} and worklog Author: ${worklog.author.accountId}")
                                 if(role.teamMember.userLink.linked.originId == worklog.author.accountId ){
                                    def tempoUserRole = tempoRoles.findAll{tempoRole ->
                                          if(role.role.self == "https://api.tempo.io/4/roles/default"){
                                            role.role.id = 2
                                          }
                                          println("Assign Role : ${role.role.id}")
                                          tempoRole.id == role.role.id
                                      }
                                    worklog.role = tempoUserRole[0].name
                                 }
                               }
                               
                             }
                           }else{
                                  println("Not match project")
                                  
                            }
                          }
                   }
                 }
             }

        }

      }
    }

     stage('Update Invoices'){
         withCredentials([
               string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero'),
               string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo'),
               string(credentialsId: xeroTenantId, variable: 'tenantIdXero')
             ])
         {
            def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
            writeFile file: "worklog.json", text: finalJson
            archiveArtifacts 'worklog.json'
            
            //update rates for worklogs
              mainJSON.teams.each { team ->
                 team.timesheets?.each { timesheet ->
                   timesheet.worklogs?.each{worklog ->
                     def filteredQuote = quoteList.Quotes.findAll{quote ->
                                    println("quote.QuoteNumber: ${quote.QuoteNumber} and worklog.account.key : ${worklog.account.key}")
                                   (quote.QuoteNumber == worklog.account.key)
                                 }
                      if(!filteredQuote.isEmpty()){
                         boolean isRateMatched = false
                         filteredQuote[0].LineItems.each{item ->
                            if(worklog.role == item.ItemCode){
                                worklog.costRate = item.UnitAmount
                                worklog.CurrencyCode = filteredQuote[0].CurrencyCode
                                worklog.TaxAmount = item.TaxAmount
                                worklog.TaxType = item.TaxType
                                isRateMatched = true
                            }
                         }
                         if(!isRateMatched){
                            def failedWorklog = JsonOutput.prettyPrint(worklog.toString())
                            println("failedWorklog : ${failedWorklog}")

                            currentBuild.result = 'ABORTED'
                            error("Quote Number: ${worklog.account.key}  Roles Are Not Matched")
                         }
                      }else{
                        currentBuild.result = 'ABORTED'
                        error("Quote Number: ${worklog.account.key} Does Not Exist")
                      }
                   }
                 }
              }

             //printing purpose
             def finalInvoiceSturcture = '''{"Invoices":[] }'''
             invoicePrintjson  = readJSON(text: finalInvoiceSturcture)

             String invoicePrefix = "Auto-"
             def i = 0
             accountKeyList.each{accountKey ->
               String invoiceNumber = String.format("%04d", i)
               def invoiceStructure = '''
                                         {
                                           "Invoices": [
                                             {
                                               "Type": "ACCREC",
                                               "Contact": {
                                                 "ContactID": ""
                                               },
                                               "DueDateString": "",
                                               "Reference": null,
                                               "Status": "DRAFT",
                                               "LineAmountTypes": "",
                                               "LineItems": [],
                                             }
                                           ]
                                         }
                                       '''
               def invoicejson  = readJSON(text: invoiceStructure)

                                //filter related quote for this account
                 def filteredQuote = quoteList.Quotes.findAll{quote ->
                                   (quote.QuoteNumber == accountKey)
                                 }
                 invoicejson.Invoices[0].Reference =  'Q:'+ filteredQuote[0].QuoteNumber + ' PO:' + filteredQuote[0].Reference
                 invoicejson.Invoices[0].Contact.ContactID = filteredQuote[0].Contact.ContactID
                   String lineAmountType = filteredQuote[0].LineAmountTypes.toLowerCase().capitalize()
                 invoicejson.Invoices[0].LineAmountTypes = lineAmountType
                 def draftInvoice = JsonOutput.prettyPrint(invoicejson.toString())
                 println("Draft Invoice : ${draftInvoice}")

                 // POST invoices
                 String postInvoicesUrl = "https://api.xero.com/api.xro/2.0/Invoices"

                 def invoiceRequestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                       [name: "xero-tenant-id", value: "${tenantIdXero}"],
                                     ]
                 if(isInvoiceInExcel){
                    invoicejson.Invoices[0].InvoiceNumber = invoicePrefix +invoiceNumber
                 }else{
                  def resultInvoice = sendPostRequest( postInvoicesUrl, draftInvoice, invoiceRequestHeaders, "XERO", "${xeroRefreshBody}${refreshTokenXero}")
                  def resultinvoiceJSON = null
                  if(resultInvoice.status == 200){
                      resultinvoiceJSON  = readJSON(text: resultInvoice.content)
                      invoicejson.Invoices[0].InvoiceNumber = resultinvoiceJSON.Invoices[0].InvoiceNumber
                  }
                 }
                 

                 // set DueDate
                 Calendar cal = Calendar.getInstance()
                 cal.add(Calendar.DAY_OF_MONTH, 31)

                 // Set timezone to UTC for formatting
                 TimeZone tz = TimeZone.getTimeZone("UTC")
                 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
                 sdf.setTimeZone(tz) // Set the timezone to UTC

                 // Format the date after 31 days from today
                 String nextMonthDate = sdf.format(cal.getTime())
                 invoicejson.Invoices[0].DueDateString = nextMonthDate
                 mainJSON.teams.each { team ->
                   team.timesheets?.each { timesheet ->
                     timesheet.worklogs?.each{worklog ->
                       invoicejson.Invoices[0].CurrencyCode = worklog.CurrencyCode
                       if(isInvoiceInExcel && isFinalInvoice){
                          invoicejson.Invoices[0].InvoiceNumber = worklog.invoiceNumber
                       }
                       if(worklog.account.key == accountKey){
                             def lineItem = ''' 
                                             {
                                               "Description": "",
                                               "UnitAmount": "",
                                               "AccountCode": 200,
                                               "ItemCode": "",
                                               "Quantity": "",
                                               "TaxAmount": "",
                                               "TaxType": ""
                                             }
                                           '''
                             def lineItemjson  = readJSON(text: lineItem)

                             def workedhours = worklog.billableSeconds /3600
                              //Set up line item description
                             String descriptionForWorklog = "${worklog.startDate} : ${worklog.description} : ${workedhours}h"
                             lineItemjson.Quantity = workedhours
                             lineItemjson.UnitAmount = worklog.costRate
                             lineItemjson.TaxAmount = worklog.TaxAmount
                             lineItemjson.TaxType = worklog.TaxType
                             //set Item code
                             boolean matchItemCode = false;
                             lineItemjson.ItemCode = worklog.role
                             if(invoicejson.Invoices[0].LineItems.isEmpty()){
                               lineItemjson.Description = descriptionForWorklog
                               invoicejson.Invoices[0].LineItems.add(lineItemjson)
                             }else{
                               invoicejson.Invoices[0].LineItems.each{item->
                                 if(item.ItemCode == lineItemjson.ItemCode && item.UnitAmount == lineItemjson.UnitAmount){
                                     item.Description = item.Description+ '\n' + descriptionForWorklog
                                     item.Quantity += workedhours
                                     matchItemCode = true
                                 }
                               }
                               if(!matchItemCode){
                                 lineItemjson.Description = descriptionForWorklog
                                 invoicejson.Invoices[0].LineItems.add(lineItemjson)
                               }
                             }
                             if(isFinalInvoice){
                               //change invoice status
                               invoicejson.Invoices[0].Status = "SUBMITTED"

                               //update worklog attribute
                                def attributeBody = '''
                                                    [
                                                      {
                                                        "attributeValues": [
                                                          {
                                                            "key": "_InvoiceNo(DONOTEDIT)_",
                                                            "value": ""
                                                          }
                                                        ],
                                                        "tempoWorklogId": ""
                                                      }
                                                    ]
                                                  '''
                                def attributeJson  = readJSON(text: attributeBody)
                                attributeJson[0].tempoWorklogId = worklog.tempoWorklogId
                                if(isInvoiceInExcel){
                                  attributeJson[0].attributeValues[0].value = worklog.invoiceNumber
                                }else{
                                  attributeJson[0].attributeValues[0].value = invoicejson.Invoices[0].InvoiceNumber
                                }
                                def finalAttribute = JsonOutput.prettyPrint(attributeJson.toString())
                                println("Final Attribute: ${finalAttribute}")
                                String attributeUrl = "https://api.tempo.io/4/worklogs/work-attribute-values"
                                
                                def requestHeaders = [[
                                                    name: "Authorization",
                                                    value: "Bearer ${tempoAccessToken}"
                                                ]]
                                def attributeResponse = sendPostRequest( attributeUrl, finalAttribute, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")

                             }
                       }
                     }
                   }
                 }
                 if(!invoicejson.Invoices[0].LineItems.isEmpty()){

                   def finalInvoice = JsonOutput.prettyPrint(invoicejson.toString())
                   if(isInvoiceInExcel){
                    invoicejson.Invoices[0].Contact.Name = filteredQuote[0].Contact.Name
                    invoicePrintjson.Invoices.add(invoicejson.Invoices[0])
                   }else{
                    // POST invoices
                    String postFinalInvoicesUrl = "https://api.xero.com/api.xro/2.0/Invoices"

                    def finalInvoiceRequestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                          [name: "xero-tenant-id", value: "${tenantIdXero}"],
                                        ]

                    def resultFinalInvoice = sendPostRequest( postFinalInvoicesUrl, finalInvoice, finalInvoiceRequestHeaders, "XERO", "${xeroRefreshBody}${refreshTokenXero}")
                    if(resultFinalInvoice.status == 200){
                      def resultInvoicejson  = readJSON(text: resultFinalInvoice.content)
                      invoicePrintjson.Invoices.add(resultInvoicejson.Invoices[0])
                    }
                   }
                 }
              i++
             }
         }
   }

  stage('WriteToCSV') {
       if(isInvoiceInExcel){

          bat('dir')
          bat('dir C:\\Jenkins\\workspace\\TIWI\\Test-Tempo-Integration\\TimesheetIntegration')


          //SXSSFWorkbook workBook = new SXSSFWorkbook(new XSSFWorkbook(new File("WIPTemplate.xslx")))
          
          println "After CO hudson context"
          
          println localFile.list()

          bat('dir')		
          
          println localFile.child("\\TimesheetIntegration\\").list()
          println localFile.child("\\TimesheetIntegration\\").child("DraftInvoice-Template.xlsx").length()
          
          XSSFWorkbook workBook = new XSSFWorkbook(localFile.child("\\TimesheetIntegration\\").child("DraftInvoice-Template.txt").read())

          println "adding Worklogs - begin"
          
          XSSFSheet sheet = workBook.getSheet("Invoices")
          DataFormat format = workBook.createDataFormat();

          XSSFCellStyle dateStyle = workBook.createCellStyle()
          XSSFDataFormat dateFormat = workBook.createDataFormat()
          dateStyle.setDataFormat(dateFormat.getFormat("yyyy-MM-dd"))


          int rowCount = 2 
          int lineItemrowCount = 2
          //print invoice
          invoicePrintjson.Invoices.each{invoice ->
            def invoiceNumber = invoice.InvoiceNumber ?: "Unknown"
            def dueDateString = invoice.DueDateString ?: "Unknown"
            Date dueDate
            if(dueDateString != "Unknown"){
                def sdf = new SimpleDateFormat("yyyy-MM-dd")
                dueDate = sdf.parse(invoice.DueDateString)

            }

            Calendar cal = Calendar.getInstance()

            // Subtract one day to get today
            cal.add(Calendar.DAY_OF_MONTH, 0)

            // Set timezone to UTC for formatting
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
            String today = sdf.format(cal.getTime())
            Date todayDate = sdf.parse(today)

            def currencyCode = invoice.CurrencyCode ?: "Unknown"
            def reference = invoice.Reference ?: "Unknown"
            def lineAmountTypes = invoice.LineAmountTypes ?: "Unknown"
            def invoiceCustomer = invoice.Contact.Name ?: "Unknown"

            XSSFRow row = sheet.createRow(rowCount)
                
            row.createCell(0)
            row.createCell(1)
            row.createCell(2)
            row.createCell(3)
            row.createCell(4)
            row.createCell(5)
            row.createCell(6)
            row?.getCell(0)?.setCellValueImpl(invoiceCustomer)
            row?.getCell(1)?.setCellValueImpl(invoiceNumber)
            row?.getCell(2)?.setCellValueImpl(reference)
            row?.getCell(3)?.setCellValueImpl(currencyCode)
            row?.getCell(4)?.setCellStyle(dateStyle)
            row?.getCell(4)?.setCellValueImpl(todayDate)
            row?.getCell(5)?.setCellStyle(dateStyle)
            row?.getCell(5)?.setCellValueImpl(dueDate)
            row?.getCell(6)?.setCellValueImpl(lineAmountTypes)
            
            rowCount++
    
            println "adding Line items - begin"
            
            itemSheet = workBook.getSheet("Line Items")

            invoice.LineItems.each{item ->
              XSSFRow itemRow = itemSheet.createRow(lineItemrowCount)

              def itemCode = item.ItemCode
              def description = item.Description
              def qty = item.Quantity
              def unitPrice = item.UnitAmount
              def taxAmount = item.TaxAmount

              itemRow.createCell(0)
              itemRow.createCell(1)
              itemRow.createCell(2)
              itemRow.createCell(3)
              itemRow.createCell(4)
              itemRow.createCell(5)
              itemRow.createCell(6)
              itemRow.createCell(7)

              itemRow?.getCell(0)?.setCellValueImpl(invoiceNumber)
              itemRow?.getCell(1)?.setCellValueImpl(itemCode)
              itemRow?.getCell(2)?.setCellValueImpl(description)
              itemRow?.getCell(3)?.setCellValueImpl(qty)
              itemRow?.getCell(4)?.setCellValueImpl(unitPrice)
              itemRow?.getCell(5)?.setCellValueImpl("Sales")
              itemRow?.getCell(6)?.setCellValueImpl(taxAmount)

              lineItemrowCount++
            }
    
          }


          println "adding Invoices - end" 
          println "writing file"
          
          OutputStream out = localFile.child("DraftInvoiceData.xlsx").write()
          workBook.write(out)
          workBook.close()
          out.close()
          

          println "writing file - Done"


          bat("dir")
          archiveArtifacts artifacts: '**.*'

       }
      //  if (!mainJSON.teams.isEmpty()) {
        //   def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
        //     writeFile file: "worklog.json", text: finalJson
        //     archiveArtifacts 'worklog.json'
      //     //  writeResponseToCSV(mainJSON)
      //      println "Timesheet data written to CSV file"
      //  }
  }    

    currentBuild.result = 'SUCCESS'
  } catch (Exception err) {
    println 'Caught an error while running the build.'
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    action = 'FINISH_LAUNCHING'
    cleanWs()
  }
}