import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import java.security.SecureRandom;
import java.Utils.*
import groovy.json.JsonOutput;
import java.text.SimpleDateFormat
import java.util.Calendar

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
tempoAccessToken = 'HhYhUG2PQoZ3HgrdMDFQqy5Umab3j2iFLlFrhVNROJE-us'
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
jiraAccessToken = 'eyJraWQiOiJhdXRoLmF0bGFzc2lhbi5jb20tQUNDRVNTLWE5Njg0YTZlLTY4MjctNGQ1Yi05MzhjLWJkOTZjYzBiOTk0ZCIsImFsZyI6IlJTMjU2In0.eyJqdGkiOiJlYzE5YmE5NC04YzQ4LTQ2NzctOGJlMC1jYjkxYmFmOWYyZDAiLCJzdWIiOiI1ZTAwOTUwYTQwMDZlYTBlYTMyNmQ3Y2MiLCJuYmYiOjE3MDkyNjUwMjQsImlzcyI6Imh0dHBzOi8vYXV0aC5hdGxhc3NpYW4uY29tIiwiaWF0IjoxNzA5MjY1MDI0LCJleHAiOjE3MDkyNjg2MjQsImF1ZCI6IjAxV3dsSkRPTXZxN2RBTGlmZldLTzBVTFA4U01PcElQIiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL3N5c3RlbUFjY291bnRFbWFpbCI6IjU3MWFmN2VkLThlZjctNDVkNS1iZjVjLWExOGZkZDY3ZTg3NkBjb25uZWN0LmF0bGFzc2lhbi5jb20iLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vcnRpIjoiMmQzNGQzZWUtNTY1Ny00YjdiLWE2ZGUtYWYxNWM1MzZjNzdlIiwiY2xpZW50X2lkIjoiMDFXd2xKRE9NdnE3ZEFMaWZmV0tPMFVMUDhTTU9wSVAiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vcmVmcmVzaF9jaGFpbl9pZCI6IjAxV3dsSkRPTXZxN2RBTGlmZldLTzBVTFA4U01PcElQLTVlMDA5NTBhNDAwNmVhMGVhMzI2ZDdjYy1lYThlMGI2Zi00MDRjLTQ1ZmMtYmRiOC1iM2IxODVhN2UyYWQiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vYXRsX3Rva2VuX3R5cGUiOiJBQ0NFU1MiLCJodHRwczovL2F0bGFzc2lhbi5jb20vZmlyc3RQYXJ0eSI6ZmFsc2UsImh0dHBzOi8vYXRsYXNzaWFuLmNvbS9zeXN0ZW1BY2NvdW50SWQiOiI3MTIwMjA6MzMyNDkzM2QtMjc5ZS00YjEwLTg0OGMtNjdhYWU4YTBkNGQwIiwiaHR0cHM6Ly9pZC5hdGxhc3NpYW4uY29tL3Nlc3Npb25faWQiOiI0MDA2YjNmZS1lMDJkLTRiOWItYmRkNS01MTZiMTc5OGY3Y2IiLCJodHRwczovL2F0bGFzc2lhbi5jb20vdmVyaWZpZWQiOnRydWUsImh0dHBzOi8vYXRsYXNzaWFuLmNvbS9lbWFpbERvbWFpbiI6ImVuYWN0b3IuY28udWsiLCJzY29wZSI6InJlYWQ6amlyYS13b3JrIG9mZmxpbmVfYWNjZXNzIHJlYWQ6amlyYS11c2VyIiwiaHR0cHM6Ly9pZC5hdGxhc3NpYW4uY29tL3Byb2Nlc3NSZWdpb24iOiJ1cy1lYXN0LTEiLCJodHRwczovL2F0bGFzc2lhbi5jb20vM2xvIjp0cnVlLCJodHRwczovL2F0bGFzc2lhbi5jb20vb3JnSWQiOiJjNmQwNDk4Zi0yMjI0LTQ4YTMtODNjYS0xMzMwNWIwMDdmMjkiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vdmVyaWZpZWQiOnRydWUsImh0dHBzOi8vaWQuYXRsYXNzaWFuLmNvbS91anQiOiJmMmU4MzIwNy0wNjIxLTQwMDMtYTY1ZC02YTMwMDg3MTgwMTkiLCJodHRwczovL2F0bGFzc2lhbi5jb20vc3lzdGVtQWNjb3VudEVtYWlsRG9tYWluIjoiY29ubmVjdC5hdGxhc3NpYW4uY29tIiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL29hdXRoQ2xpZW50SWQiOiIwMVd3bEpET012cTdkQUxpZmZXS08wVUxQOFNNT3BJUCJ9.cOs3yClNNczIThbV9V7OAtAYR-9UI24QiUdvs9QMmVVvBViVY1fpNPNp_ZQsbpTt8O8HgmCQlc74ooFPC6hg79RXW9PR3w20kWjaoiTUFy_bUHeaep9MJuzuG2-mbfmrfWHDKSxs03o6e_R4Lqnh1FfUVItEzeS_hOfprLLnfIfnUUQ70MArI6UxqKI-N23BYqS3CuCdeMsv7Y7JcvoyBr9fh03Y5z5M1ixIRMEZEMc8hctfet_hAlSaZLU9H8atuDkuswxEdtEu9DKPfdoQTf8nVLMIW3QGEsjBqgMjwNIZUaDDMkA2WQBWPJimn6Am5GHgFcgIQbYVbZms2_8l6A'
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
  println("refrsh token method: ${payload}")
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
   println("user detals: ${userResponse.content}")

   def userJson  = readJSON(text: userResponse.content)
   return userJson
}

def writeResponseToCSV(mainJSON) {
    def csvContent = new StringBuilder()
    
    // Add title to CSV content
    csvContent.append("Work LOG ID, ISSUE ID, BILLABLE SECOND, DESCRIPTION, CREATED AT, UPDATED AT, STATUS, REVIEWER, USER, ATTRIBUTE, TEAM \n")

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
            csvContent.append("${worklog.tempoWorklogId},${worklog.issue.key},${worklog.billableSeconds},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${timesheet.user.displayName},${attributeVal},${team.name}\n")
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

                  // Calculate last Monday and last Sunday
                  Calendar cal = Calendar.getInstance()
                  int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                  int daysToLastSunday = Calendar.SUNDAY - dayOfWeek

                  if (daysToLastSunday == 0) {
                      daysToLastSunday = -7 // Last week Sunday
                  }
                  
                  cal.add(Calendar.DAY_OF_YEAR, daysToLastSunday)
                  Date lastSunday = cal.getTime()
                  cal.add(Calendar.DAY_OF_YEAR, -6)
                  Date lastMonday = cal.getTime()

                  // Format dates
                  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
                  String startDate = sdf.format(lastMonday)
                  String endDate = sdf.format(lastSunday)

                  println("startDate : ${startDate} and EndDate : ${endDate}")
                  

                  // String fetchTimeSheetUrl = "https://api.tempo.io/4/timesheet-approvals/team/${teamId}?from=${startDate}&to=${endDate}"
                  String fetchTimeSheetUrl = "https://api.tempo.io/4/timesheet-approvals/team/${teamId}?from=2024-02-26&to=2024-03-03"

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
                        team.timesheets = timeSheetjson.results
                      }else{
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
                  println("Time sheet status : ${timesheet.status}")
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
                        println("worklog: ${worLogResponse.content}")
                        def workLogjson  = readJSON(text: worLogResponse.content)
                        timesheet.worklogs = workLogjson.results
                      }else{
                      }
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
                    println("issue detals: ${issueResponse.content}")

                    def issueJson  = readJSON(text: issueResponse.content)
                    worklog.issue.key = issueJson.key
                    worklog.issue.summery = issueJson.fields.summary 
        
                  }
                } 
          }
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

