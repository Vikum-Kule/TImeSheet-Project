import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import java.security.SecureRandom;
import java.Utils.*
import groovy.json.JsonOutput;

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
jiraAccessToken = 'eyJraWQiOiJhdXRoLmF0bGFzc2lhbi5jb20tQUNDRVNTLWE5Njg0YTZlLTY4MjctNGQ1Yi05MzhjLWJkOTZjYzBiOTk0ZCIsImFsZyI6IlJTMjU2In0.eyJqdGkiOiJlOGQ4MDBlMi1iYTBjLTRkN2UtOTkxNy04YjBmZWUzYWM1ZWQiLCJzdWIiOiI1ZTAwOTUwYTQwMDZlYTBlYTMyNmQ3Y2MiLCJuYmYiOjE3MDkxOTg0NjYsImlzcyI6Imh0dHBzOi8vYXV0aC5hdGxhc3NpYW4uY29tIiwiaWF0IjoxNzA5MTk4NDY2LCJleHAiOjE3MDkyMDIwNjYsImF1ZCI6IjAxV3dsSkRPTXZxN2RBTGlmZldLTzBVTFA4U01PcElQIiwic2NvcGUiOiJvZmZsaW5lX2FjY2VzcyByZWFkOmppcmEtdXNlciByZWFkOmppcmEtd29yayIsImh0dHBzOi8vYXRsYXNzaWFuLmNvbS9zeXN0ZW1BY2NvdW50RW1haWwiOiI1NzFhZjdlZC04ZWY3LTQ1ZDUtYmY1Yy1hMThmZGQ2N2U4NzZAY29ubmVjdC5hdGxhc3NpYW4uY29tIiwiY2xpZW50X2lkIjoiMDFXd2xKRE9NdnE3ZEFMaWZmV0tPMFVMUDhTTU9wSVAiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vYXRsX3Rva2VuX3R5cGUiOiJBQ0NFU1MiLCJodHRwczovL2F0bGFzc2lhbi5jb20vZmlyc3RQYXJ0eSI6ZmFsc2UsImh0dHBzOi8vYXRsYXNzaWFuLmNvbS9zeXN0ZW1BY2NvdW50SWQiOiI3MTIwMjA6MzMyNDkzM2QtMjc5ZS00YjEwLTg0OGMtNjdhYWU4YTBkNGQwIiwiaHR0cHM6Ly9pZC5hdGxhc3NpYW4uY29tL3Nlc3Npb25faWQiOiI0MDA2YjNmZS1lMDJkLTRiOWItYmRkNS01MTZiMTc5OGY3Y2IiLCJodHRwczovL2F0bGFzc2lhbi5jb20vdmVyaWZpZWQiOnRydWUsImh0dHBzOi8vYXRsYXNzaWFuLmNvbS9lbWFpbERvbWFpbiI6ImVuYWN0b3IuY28udWsiLCJ2ZXJpZmllZCI6InRydWUiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vcHJvY2Vzc1JlZ2lvbiI6InVzLWVhc3QtMSIsImh0dHBzOi8vaWQuYXRsYXNzaWFuLmNvbS9yZWZyZXNoX2NoYWluX2lkIjoiMDFXd2xKRE9NdnE3ZEFMaWZmV0tPMFVMUDhTTU9wSVAtNWUwMDk1MGE0MDA2ZWEwZWEzMjZkN2NjLWQ1NGY3NDk5LTFhYzgtNDNmMS05ZTM2LTRkNDc5MjQ1MzUzZiIsImh0dHBzOi8vaWQuYXRsYXNzaWFuLmNvbS91anQiOiI3NDY5MDFlMC1kZmFmLTRmMDAtYjZhZS0zY2E2MGU3ZDRkZDgiLCJodHRwczovL2F0bGFzc2lhbi5jb20vM2xvIjp0cnVlLCJodHRwczovL2F0bGFzc2lhbi5jb20vb3JnSWQiOiJjNmQwNDk4Zi0yMjI0LTQ4YTMtODNjYS0xMzMwNWIwMDdmMjkiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vdmVyaWZpZWQiOnRydWUsImh0dHBzOi8vaWQuYXRsYXNzaWFuLmNvbS9ydGkiOiI3OTA5ZTVkYi02ZmNkLTQxMDctYjEwNS05ZjNhMjA3ODRiYjgiLCJodHRwczovL2F0bGFzc2lhbi5jb20vc3lzdGVtQWNjb3VudEVtYWlsRG9tYWluIjoiY29ubmVjdC5hdGxhc3NpYW4uY29tIiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL29hdXRoQ2xpZW50SWQiOiIwMVd3bEpET012cTdkQUxpZmZXS08wVUxQOFNNT3BJUCJ9.Crs1j0tij4esWBYEKPVvpFX85CjLWO0AfSzeWwrQYc1hHbWyIdXdci7h-OmMxiSNWLPswOH6xnJWRr0OYvdWqJjuhWo2SOO61qd6Gj_8vWsEwJcYYW_ouJ_ZhDSfHvoXOPW25F61s_OCrQOQgM6AOJ6K6_8p35oF0okNtt0pSR7wEBUU9zfck73vQuw2v7Ne9Ykv9BSkvvIYfL1ulnRIwKeUjPMGY7-ttwzHH4-UNdCtFQdm1GxuRjsLkboK_oI7LExRnXImYhLUmfSxU5lVM5uMSXrh9YQmARK2gVJwVVSFp1lDh7ARlNU9cN67pLSCy0NcQpYVGbFeFw28hsdqkw'
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
          // def tempoTokenPayload = refreshTokens(tempoRefreshUrl, refreshTokenPayload)
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
    csvContent.append("Work LOG ID, BILLABLE SECOND, DESCRIPTION, CREATED AT, UPDATED AT, STATUS, REVIEWER, TEAM \n")

    // Iterate over results and append to CSV content
    mainJSON.teams.each { team ->
        team.timesheets?.each { timesheet ->
          timesheet.worklogs?.each{worklog ->

            csvContent.append("${worklog.tempoWorklogId},${worklog.billableSeconds},${worklog.description},${worklog.createdAt},${worklog.updatedAt},${timesheet.status.key},${timesheet.reviewer.displayName},${team.name}\n")
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
        def finalJSON = JsonOutput.prettyPrint(mainJSON.toString())
        println("after worklogs fetch: ${finalJSON}")  
      }
      }
    }
    stage('WriteToCSV') {
         if (teamResponse) {
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

