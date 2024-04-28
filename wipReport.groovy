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

import org.json.JSONArray;
import org.json.JSONObject;
import hudson.FilePath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;



import java.io.File

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper
//credentials
String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'
String tempoCode = 'TEMPO_CODE'
String jiraClientId = 'JIRA_CLIENT_ID'
String jiraClientSecret = 'JIRA_CLIENT_SECRET'
String jiraCode = 'JIRA_CODE' 
String xeroClientId = 'XERO_CLIENT_ID'
String xeroClientSecret = 'XERO_CLIENT_SECRET'
Boolean isFinalInvoice = params.Final_Invoice_Generation
String finalDate = (params.FINAL_DATE == null) ? "" : params.FINAL_DATE

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
invoicesJSON = null

def quoteList
workLogList = []
String tempoRefreshBody
String jiraRefreshBody
def xeroRefreshBody

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
    def response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'GET',
                               validResponseCodes: '200, 401,403, 404')
    // Check if the request was successful or not
    if (response.status == 401){
      if (platform == "TEMPO"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tempoAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)
      }else if (platform == "JIRA"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${jiraAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)

      }else if(platform == "XERO"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${xeroAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)

      }
    }
    else{
      println(response)
      return response
    }
}


def sendPostRequest( url, payload, header, platform, refreshTokenPayload) {    
    def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    url: url
    if (response.status == 401){
      if(platform == "XERO"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${xeroAccessToken}"
              }
          }
          sendPostRequest( url, payload, header, platform, refreshTokenPayload)
      }else if (platform == "TEMPO"){
          //update header with new access token
          refreshTokens(platform, refreshTokenPayload)
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tempoAccessToken}"
              }
          }
          sendPostRequest( url, payload, header, platform, refreshTokenPayload)
      }
    }else{
      println(response)
      return response
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
          break
      case "XERO":
          println "Xero Refresh Token Update"
          String xeroRefreshUrl = 'https://identity.xero.com/connect/token?='
          def xeroHeader = [[name: "Content-Typen",value: 'application/x-www-form-urlencoded']]
          def xeroTokenPayload = refreshTokensRequest(xeroRefreshUrl, refreshTokenPayload, xeroHeader )
          // save on disk
          def jsonResponse = readJSON text: xeroTokenPayload.content
          xeroAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, xeroRefreshToken)
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
            string(credentialsId: jiraCode, variable: 'codeJira'),
            string(credentialsId: xeroClientId, variable: 'clientIdXero'),
            string(credentialsId: xeroClientSecret, variable: 'clientSecretXero')
      ]){
        //prepare payload for refresh tokens
        tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
        jiraRefreshBody = "grant_type=refresh_token&client_id=${clientIdJira}&client_secret=${clientSecretJira}&code=${codeJira}&redirect_uri=https://enactor.co/&refresh_token="
        xeroRefreshBody = "grant_type=refresh_token&client_id=${clientIdXero}&client_secret=${clientSecretXero}&refresh_token="
      }
    }
    stage("Checkout Template Files") {
        
        bat('dir')
       
        // Checking out the branch
		def svnBranch = "https://dev.enactor.co.uk/svn/dev/Resources/Jenkins/Groovy/TimesheetIntegration"
		echo "Checking out branch ${svnBranch}"
		checkoutSVN("TimesheetIntegration/", svnBranch, svnCredentialsId)
		
		
				
    }
    stage('Fetch WIP Worklogs from Tempo') { 
       
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
                        
                        println("startDate : ${startDate} and EndDate : ${endDate}")
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
                                        Date createdAt = sdf.parse(workLog.createdAt)
                                        Date updatedAt = sdf.parse(workLog.updatedAt)
                                        boolean isAccountExist = false
                                        boolean isInvoiceExist = false
                                        workLog.attributes.values?.each{attribute ->
                                            String val = attribute.value
                                            if (attribute.key == '_TempoAccount_' && val != 'null') {
                                                isAccountExist = true
                                            }

                                            if(attribute.key == '_InvoiceNo(DONOTEDIT)_' && attribute.value){
                                              isInvoiceExist = true
                                            }
                                        }
                                        (!createdAt.after(lastDayOfLastMonth) ||!updatedAt.after(lastDayOfLastMonth)) && isAccountExist && !isInvoiceExist
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
                    def ignoreRoles = ['Member', 'Team Lead']
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
                                    println("Attribute Val: ${val}")
                                    if(attribute.key == '_TempoAccount_' && val != 'null'){
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
                                        println("Final account: ${finalAccount}")
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

        stage('fetch customers'){
            if(!mainJSON.teams.isEmpty()){
                withCredentials([
                    string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
                    ])
                {
                    mainJSON.teams.each { team ->
                            team.timesheets?.each { timesheet ->
                                timesheet.worklogs?.each{worklog ->
                                    def accountSearchBody = '''
                                                            {
                                                                "keys": [],
                                                                "statuses": [
                                                                "OPEN"
                                                                ]
                                                            } 
                                                            '''
                                    def accountSearchJson  = readJSON(text: accountSearchBody)
                                    worklog.attributes.values?.each{attribute ->
                                        if(attribute.key == '_TempoAccount_' && attribute.value){
                                            accountSearchJson.keys[0] = attribute.value
                                        }
                                    }
                                    def searchAccount = JsonOutput.prettyPrint(accountSearchJson.toString())
                                    String searchAccountUrl = "https://api.tempo.io/4/accounts/search"
                                    
                                    def requestHeaders = [[
                                                        name: "Authorization",
                                                        value: "Bearer ${tempoAccessToken}"
                                                    ]]
                                    def accountResponse = sendPostRequest( searchAccountUrl, searchAccount, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")
                                    if(accountResponse){
                                        if(accountResponse.status == 200){
                                        def accResponseJSON  = readJSON(text: accountResponse.content)
                                        worklog.customer = accResponseJSON.results[0].customer
                                        }
                                    }
                                }
                            } 
                    }
                }
            }
        }

    }

    stage('CostTracking'){
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
                              project.name == "${worklog.account.name}-Cost"
                          }
                          if(!costProject.isEmpty()){
                            //fetch rates
                            String rateUrl = costProject[0].rates.self
                            def rateRequestHeaders = [[
                                                  name: "Authorization",
                                                  value: "Bearer ${tempoAccessToken}"
                                                  ]]
                            rateResponse = sendGetRequest(rateUrl, rateRequestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
                            if(rateResponse.status == 200){
                              def rateJSON = readJSON(text: rateResponse.content)
                              def currentDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                              def closestObject = null
                              def closestDifference = Long.MAX_VALUE
                              rateJSON.rates.each{ rate->
                                if(rate.teamMember.userLink.linked.originId == worklog.author.accountId ){
                                    rate.costRates.values.each{ costVal->
                                        if (costVal.containsKey("effectiveDate")) {
                                            def dateFormat = new SimpleDateFormat("yyyy-MM-dd")
                                            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
                                            def effectiveDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                                            effectiveDate.setTime(dateFormat.parse(rate["effectiveDate"]))
                                            def difference = Math.abs(currentDate.timeInMillis - effectiveDate.timeInMillis)
                                            if (difference < closestDifference) {
                                                closestDifference = difference
                                                worklog.costRate  = rate
                                            }
                                        } else {
                                            worklog.costRate = rate // If no effective date provided, consider it as closest
                                        }

                                    }
                                    
                                }
                              }
                            }
                          }
                  }
                }
            }

          }

        }
      }
      def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
        writeFile file: "worklogs.json", text: finalJson
        archiveArtifacts 'worklogs.json'
    }

    stage('Fetch Invoices'){
        withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero')
            ])
        {
            //get invoices
            String fetchInvoicesUrl = "https://api.xero.com/api.xro/2.0/Invoices"

            def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                        [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                                      ]
            def invoicesResponse = sendGetRequest(fetchInvoicesUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
            if(invoicesResponse.status == 200){
                invoicesJSON = readJSON(text: invoicesResponse.content)
                if(!invoicesJSON.Invoices.isEmpty()){
                    def finalInvoices = JsonOutput.prettyPrint(invoicesJSON.toString())
                    writeFile file: "invoices.json", text: finalInvoices
                    archiveArtifacts 'invoices.json'
                }
            }
                                
        }
    }


  stage('Generate Excel Sheet') {
      
    bat('dir')
    bat('dir C:\\Jenkins\\workspace\\TIWI\\WIP-Report\\TimesheetIntegration')


    //SXSSFWorkbook workBook = new SXSSFWorkbook(new XSSFWorkbook(new File("WIPTemplate.xslx")))
    
    println "After CO hudson context"
		
    println localFile.list()

	bat('dir')		
	
	println localFile.child("\\TimesheetIntegration\\").list()
    println localFile.child("\\TimesheetIntegration\\").child("WIPTemplate.txt").length()
    
    XSSFWorkbook workBook = new XSSFWorkbook(localFile.child("\\TimesheetIntegration\\").child("WIPTemplate.txt").read())
    
    
    int rowCount = 2 //start inserting at this row 0 - header 1 - formulars
    
    println "adding Worklogs - begin"
    
    XSSFSheet sheet = workBook.getSheet("WIPWorklogData")
    
    //print worklog data
    mainJSON.teams?.each { team ->
        team.timesheets?.each { timesheet ->
            timesheet.worklogs?.each { worklog ->
                def customerName = worklog.customer?.name ?: "Unknown"
                def tempoWorklogId = worklog?.tempoWorklogId ?: "Unknown"
                def timeSpentSeconds = worklog?.timeSpentSeconds ?: 0
                def costRateValue 
                if(worklog.costRate){
                    costRateValue = worklog.costRate?.costRates?.values[0]?.rate?.value
                }else{
                    costRateValue = 0
                }
                println "adding Worklogs"
                println("${customerName} | ${tempoWorklogId} | ${timeSpentSeconds} | ${costRateValue}")
                
                XSSFRow row = sheet.createRow(rowCount)
                
                row.createCell(0)
                row.createCell(1)
                row.createCell(2)
                row.createCell(3)
                
                println "row ${row}" 
                row?.getCell(0)?.setCellValueImpl("${customerName}")
                
                println "row ${row?.getCell(0)}"
                println "Cell 0  ${row.getCell(0).getStringCellValue()}" 

                row?.getCell(1)?.setCellValueImpl("${tempoWorklogId}")
                row?.getCell(2)?.setCellValueImpl("${timeSpentSeconds}")
                row?.getCell(3)?.setCellValueImpl("${costRateValue}")
                rowCount++
            }
        }
    }
    println "adding Worklogs - End"

    rowCount = 2
    
    println "adding Invoices - begin"
    
    sheet = workBook.getSheet("InvoiceData")
    
    //print invoice data
    invoicesJSON.Invoices?.each { invoice ->
        println("Invoice Status: ${invoice.Status}")
        if(invoice.Status != "DELETED" && invoice.Status != "DRAFT"){
            def accountBody = '''
                            {
                                "keys": [],
                                "statuses": [
                                "OPEN"
                                ]
                            }
                            '''
            def accountJson  = readJSON(text: accountBody)
            accountJson.keys.add(invoice.Reference)
            def finalAccount = JsonOutput.prettyPrint(accountJson.toString())
            String accountUrl = "https://api.tempo.io/4/accounts/search"
            def reponseAccountJSON = null
            def requestHeaders = [[
                                name: "Authorization",
                                value: "Bearer ${tempoAccessToken}"
                            ]]
            withCredentials([
                string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
                ])
            {
                def accountResponse = sendPostRequest( accountUrl, finalAccount, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")
                if(accountResponse.status == 200){
                    reponseAccountJSON  = readJSON(text: accountResponse.content)
                }
            }
            def contactName = invoice.Contact?.Name ?: "Unknown"
            def invoiceNumber = invoice.InvoiceNumber ?: "Unknown"
            def reference = "Unknown"
            if( !reponseAccountJSON.results.isEmpty()){
                reference = reponseAccountJSON.results[0].name
            }
            def amountPaid = invoice.AmountPaid ?: 0
            def amountDue = invoice.AmountDue ?: 0
            def status = invoice.Status ?: "Unknown"
            def dueDateString = invoice.DueDateString ?: "Unknown"
            def quoteLink = " "
            def currencyCode = invoice.CurrencyCode ?: "Unknown"
            def formattedDate
            if(invoice.FullyPaidOnDate){
                def fullyPaidOnDateMillis = invoice.FullyPaidOnDate.replaceAll("/Date\\((\\d+)\\+\\d+\\)/", '$1').toLong()

                // Convert to yyyy-mm-dd format
                def sdf = new SimpleDateFormat("yyyy-MM-dd")
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
                formattedDate = sdf.format(new Date(fullyPaidOnDateMillis))
            }else{
                formattedDate = "NOT EXIST"
            }

            println("${contactName} | ${invoiceNumber} | ${reference} | ${amountPaid} | ${amountDue} | ${status} | ${dueDateString} | ${quoteLink} | ${currencyCode} | ${formattedDate}")
            
            
            XSSFRow row = sheet.getRow(rowCount)
            
            for(cellCount in 0..9) { //Very unusual
                if (row?.getCell(cellCount) == null)
                    row?.createCell(cellCount)
            }
            
            row?.getCell(0)?.setCellValueImpl("${contactName}")
            row?.getCell(1)?.setCellValueImpl("${invoiceNumber}")
            row?.getCell(2)?.setCellValueImpl("${reference}")
            row?.getCell(3)?.setCellValueImpl("${amountPaid}")
            row?.getCell(4)?.setCellValueImpl("${amountDue}")
            row?.getCell(5)?.setCellValueImpl("${status}")
            row?.getCell(6)?.setCellValueImpl("${dueDateString}")
            row?.getCell(7)?.setCellValueImpl("${quoteLink}")
            row?.getCell(8)?.setCellValueImpl("${currencyCode}")
            row?.getCell(9)?.setCellValueImpl("${formattedDate}")
            
            rowCount++
        }
    }
    
    println "adding Invoices - end" 
    println "writing file"
    
    OutputStream out = localFile.child("WIPReport.xslx").write()
    workBook.write(out)
    workBook.close()
    out.close()
    

    println "writing file - Done"


    bat("dir")
        
    }
    
   
    


        
        
    archiveArtifacts artifacts: '**.*'

    currentBuild.result = 'SUCCESS'
  } catch (Exception err) {
    println 'Caught an error while running the build. Saving error log in the database.'
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    
    action = 'FINISH_LAUNCHING'
    cleanWs()
  }
}

