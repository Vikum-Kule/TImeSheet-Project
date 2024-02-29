import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.*
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import java.security.SecureRandom;
import java.Utils.*
import groovy.json.JsonSlurper
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
jiraAccessToken = 'eyJraWQiOiJhdXRoLmF0bGFzc2lhbi5jb20tQUNDRVNTLWE5Njg0YTZlLTY4MjctNGQ1Yi05MzhjLWJkOTZjYzBiOTk0ZCIsImFsZyI6IlJTMjU2In0.eyJqdGkiOiJiY2FmMTY0MS1hMTE4LTRjZDItOTQ3ZS1mZDZiMDhlYTYyY2IiLCJzdWIiOiI1ZTAwOTUwYTQwMDZlYTBlYTMyNmQ3Y2MiLCJuYmYiOjE3MDkxOTE2MTUsImlzcyI6Imh0dHBzOi8vYXV0aC5hdGxhc3NpYW4uY29tIiwiaWF0IjoxNzA5MTkxNjE1LCJleHAiOjE3MDkxOTUyMTUsImF1ZCI6IjAxV3dsSkRPTXZxN2RBTGlmZldLTzBVTFA4U01PcElQIiwic2NvcGUiOiJvZmZsaW5lX2FjY2VzcyByZWFkOmppcmEtdXNlciByZWFkOmppcmEtd29yayIsImh0dHBzOi8vaWQuYXRsYXNzaWFuLmNvbS9ydGkiOiIyYzYyY2I3NC1iMzIxLTQxMjUtODc3Yi02ZTkyYTljNzJlY2IiLCJodHRwczovL2F0bGFzc2lhbi5jb20vc3lzdGVtQWNjb3VudEVtYWlsIjoiNTcxYWY3ZWQtOGVmNy00NWQ1LWJmNWMtYTE4ZmRkNjdlODc2QGNvbm5lY3QuYXRsYXNzaWFuLmNvbSIsImNsaWVudF9pZCI6IjAxV3dsSkRPTXZxN2RBTGlmZldLTzBVTFA4U01PcElQIiwiaHR0cHM6Ly9pZC5hdGxhc3NpYW4uY29tL2F0bF90b2tlbl90eXBlIjoiQUNDRVNTIiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL2ZpcnN0UGFydHkiOmZhbHNlLCJodHRwczovL2F0bGFzc2lhbi5jb20vc3lzdGVtQWNjb3VudElkIjoiNzEyMDIwOjMzMjQ5MzNkLTI3OWUtNGIxMC04NDhjLTY3YWFlOGEwZDRkMCIsImh0dHBzOi8vaWQuYXRsYXNzaWFuLmNvbS9zZXNzaW9uX2lkIjoiNDAwNmIzZmUtZTAyZC00YjliLWJkZDUtNTE2YjE3OThmN2NiIiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL3ZlcmlmaWVkIjp0cnVlLCJodHRwczovL2F0bGFzc2lhbi5jb20vZW1haWxEb21haW4iOiJlbmFjdG9yLmNvLnVrIiwidmVyaWZpZWQiOiJ0cnVlIiwiaHR0cHM6Ly9pZC5hdGxhc3NpYW4uY29tL3Byb2Nlc3NSZWdpb24iOiJ1cy1lYXN0LTEiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vcmVmcmVzaF9jaGFpbl9pZCI6IjAxV3dsSkRPTXZxN2RBTGlmZldLTzBVTFA4U01PcElQLTVlMDA5NTBhNDAwNmVhMGVhMzI2ZDdjYy1kNTRmNzQ5OS0xYWM4LTQzZjEtOWUzNi00ZDQ3OTI0NTM1M2YiLCJodHRwczovL2lkLmF0bGFzc2lhbi5jb20vdWp0IjoiNzQ2OTAxZTAtZGZhZi00ZjAwLWI2YWUtM2NhNjBlN2Q0ZGQ4IiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tLzNsbyI6dHJ1ZSwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL29yZ0lkIjoiYzZkMDQ5OGYtMjIyNC00OGEzLTgzY2EtMTMzMDViMDA3ZjI5IiwiaHR0cHM6Ly9pZC5hdGxhc3NpYW4uY29tL3ZlcmlmaWVkIjp0cnVlLCJodHRwczovL2F0bGFzc2lhbi5jb20vc3lzdGVtQWNjb3VudEVtYWlsRG9tYWluIjoiY29ubmVjdC5hdGxhc3NpYW4uY29tIiwiaHR0cHM6Ly9hdGxhc3NpYW4uY29tL29hdXRoQ2xpZW50SWQiOiIwMVd3bEpET012cTdkQUxpZmZXS08wVUxQOFNNT3BJUCJ9.Ks5ZV91EMXzltT5DhKdmEPPPi5-Jl7tVNumAxkZw3OTnTneuY5bKgwX6SuacST09y7B79SZ9thxAQKFaYH_CCV6pmb-KfKQpsA4xigBgJeYLaEgVjaJMzYHbYnHrjZ6pyWJdUUOxTEIx2bmE_mWFZDPEcV7OhNNDKOhzZbSiz3AlOlsxQP-YraRpROaGfvSOZO8r0i82QdGNSMvoX10aXd1vH6meWLvUzkgwKUuoUkvdwPAS4KVZ0u0l2lAV4yD8ERuch3YT4pQ5dwTkf_i8Qd1kRafy10EciojOESKUVZ_C3RWPz1jlGoZ-7fwdfTW_zIGXYbZf9JDPhe699r4kwA'
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
          updateTokens(refreshTokent, jiraRefreshToken)

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
  println("refrsh token method")
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
   def userResponse = sendGetRequest("${userBaseUrl}${userId}", jiraActorRequestHeaders, "JIRA", "${refreshBody}&${refreshToken}" )
   println("user detals: ${userResponse.content}")

   def userJson  = readJSON(text: userResponse.content)
   return userJson
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
        teamResponse = sendGetRequest(fetchTeamUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}&${refreshTokenTempo}" )

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
                  def timeSheetResponse = sendGetRequest(fetchTimeSheetUrl, requestHeaders, "TEMPO","${tempoRefreshBody}&${refreshTokenTempo}" )

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
                println("Team JSON: ${mainJSON}")              
                
            }
      }     
    }
    stage('FetchUsers'){
      if(teamResponse){
        withCredentials([
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ])
        {
          println("jira refresh token : ${refreshTokenJira}")
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

        println("Team after user fetch: ${mainJSON}")  
      }
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

