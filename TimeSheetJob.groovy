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

//tokens
tempoAccessToken = null
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
jiraAccessToken = null
jiraRefreshToken = 'JIRA_REFRESH_TOKEN' 

Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
String jobExecutionNode = 'master'
teamResponse = null
mainJSON = null

String tempoRefreshBody
String jiraRefreshBody


def sendGetRequest(url, header, platform, refreshTokenPayload) {
    def response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'GET',
                               validResponseCodes: '200, 401,403, 404')
    // Check if the request was successful or not
    if (response.status == 401){
      if (platform == "TEMPO"){
          String tempoRefreshUrl = 'https://api.tempo.io/oauth/token/?grant_type=&client_id=&client_secret=&refresh_token'
          def tempoTokenPayload = refreshTokens(tempoRefreshUrl, refreshTokenPayload)
          // save on disk
          def jsonResponse = readJSON text: tempoTokenPayload.content
          tempoAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, tempoRefreshToken)

          //update header with new access token
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tempoAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshTokenPayload)
      }else if (platform == "JIRA"){
          String tempoRefreshUrl = 'https://auth.atlassian.com/oauth/token'
          def jiraTokenPayload = refreshTokens(tempoRefreshUrl, refreshTokenPayload)
          // save on disk
          def jsonResponse = readJSON text: jiraTokenPayload.content
          jiraAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken, jiraRefreshToken)

          //update header with new access token
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${jiraAccessToken}"
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


def sendPostRequest( url, payload, header, contentType) {    
    print(payload)
    def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: header,
                    httpMode: 'POST',
                    requestBody: payload,
                    url: callbackUrl
    
    println(response)
}

def refreshTokens (url, payload){
  def response = httpRequest acceptType: 'APPLICATION_JSON',
                    contentType: 'APPLICATION_FORM',
                    customHeaders: [[
                                  name: "Content-Typen",
                                  value: "application/x-www-form-urlencoded"
                              ]],
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

def writeResponseToCSV(mainJSON) {
    def csvContent = new StringBuilder()
    
    // Add title to CSV content
    csvContent.append("WORK LOG ID, ISSUE ID, BILLABLE SECONDS,START DATE, DESCRIPTION, CREATED AT, UPDATED AT, STATUS, REVIEWER, USER, ACCOUNT KEY, TEAM \n")

    // Iterate over results and append to CSV content
    mainJSON.teams.each { team ->
        team.timesheets?.each { timesheet ->
          timesheet.worklogs?.each{worklog ->
            def attributeVal = '-'
            worklog.attributes.values?.each{attribute ->
              if(attribute.key == '_TempoAccount_'){
                attributeVal = attribute.value 
              }
            }
            csvContent.append("${worklog.tempoWorklogId},${worklog.issue.key},${worklog.billableSeconds},${worklog.startDate},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${timesheet.user.displayName},${attributeVal},${team.name}\n")
          }         
        }
    }
    // Write content to CSV file
    writeFile file: "timeSheet.csv", text: csvContent.toString()
    archiveArtifacts 'timeSheet.csv'
}

node('master') {
  try {
    stage('Preparation') {
      cleanWs()
      withCredentials([
            string(credentialsId: tempoClientId, variable: 'clientIdTempo'),
            string(credentialsId: tempoClientSecret, variable: 'clientSecretTempo'),
            string(credentialsId: jiraClientId, variable: 'clientIdJira'),
            string(credentialsId: jiraClientSecret, variable: 'clientSecretJira'),
            string(credentialsId: jiraCode, variable: 'codeJira')
      ]){
        //prepare payload for refresh tokens
        tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
        jiraRefreshBody = "grant_type=refresh_token&client_id=${clientIdJira}&client_secret=${clientSecretJira}&code=${codeJira}&redirect_uri=https://enactor.co/&refresh_token="

      }
    }
    stage('GetAllTeams'){
      String fetchTeamUrl = "https://api.tempo.io/4/teams"
      withCredentials([
            string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
      ]){
        def requestHeaders = [[
                                  name: "Authorization",
                                  value: "Bearer ${tempoAccessToken}"
                              ]]
        teamResponse = sendGetRequest(fetchTeamUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )

      }
    }

    stage('FetchTimeSheets'){
      if(teamResponse){
          withCredentials([
              string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo'),
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ]){
                mainJSON  = readJSON(text: teamResponse.content)
                //remove properties that are not needed
                mainJSON.remove('self')
                mainJSON.remove('metadata')

                //rename feilds
                mainJSON.teams = mainJSON.results
                mainJSON.remove('results')

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
                  for(int i = 0; i < 8; i++) {
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
                          println("timeSheet results: ${timeSheetjson.results}")
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
      if(teamResponse){
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
      if(teamResponse){
        withCredentials([
              string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
            ])
        {
          // Get current date/time
          Calendar cal = Calendar.getInstance()

          // Subtract one day to get yesterday
          cal.add(Calendar.DAY_OF_MONTH, -1)

          // Set timezone to UTC for formatting
          TimeZone tz = TimeZone.getTimeZone("UTC")
          SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
          sdf.setTimeZone(tz) // Set the timezone to UTC

          // Format yesterday's date
          // String yesterdayDate = sdf.format(cal.getTime())
          String yesterdayDate = "2024-03-01"
          println("yesterdate")

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
                              String createdDate = workLog.createdAt
                              String updatedDate = workLog.updatedAt

                              def dateOfCreatedDate = createdDate.split("T")[0]
                              def dateOfUpdatedDate = updatedDate.split("T")[0]

                              (dateOfCreatedDate == yesterdayDate || dateOfUpdatedDate == yesterdayDate)
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
      }
      }
    }

    stage('FetchJiraTickets'){
      if(teamResponse){
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

                    def issueJson  = readJSON(text: issueResponse.content)
                    worklog.issue.key = issueJson.key
                    worklog.issue.summery = issueJson.fields.summary 
        
                  }
                } 
          }
            def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
        }
      }
    }

    stage('WriteToCSV') {
         if (teamResponse) {
            def finalJson = JsonOutput.prettyPrint(mainJSON.toString())
             println("final result: ${finalJson}")
             writeResponseToCSV(mainJSON)
             println "Timesheet data written to CSV file"
         }
    }    
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

