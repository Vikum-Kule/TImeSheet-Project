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

//tokens
tempoAccessToken = 'HhYhUG2PQoZ3HgrdMDFQqy5Umab3j2iFLlFrhVNROJE-us'
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
jiraAccessToken = null
jiraRefreshToken = null 

Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
String jobExecutionNode = 'master'
teamResponse = null
mainJSON = null


def sendGetRequest(url, header, platform, refreshPayload) {
    def response = httpRequest(url: url,
                               customHeaders: header ,
                               httpMode: 'GET',
                               validResponseCodes: '200, 401,403, 404')
    // Check if the request was successful or not
    if (response.status == 401){
      if (platform == "TEMPO"){
          String tempoRefreshUrl = 'https://api.tempo.io/oauth/token/?grant_type=&client_id=&client_secret=&refresh_token'
          // def tempoTokenPayload = refreshTokens(tempoRefreshUrl, refreshPayload)
          // save on disk
          def jsonResponse = readJSON text: tempoTokenPayload.content
          tempoAccessToken = jsonResponse.access_token
          def refreshToken = jsonResponse.refresh_token
          updateTokens(refreshToken)

          //update header with new access token
          header.each { data ->
              if (data.name == "Authorization") {
                  data.value = "Bearer ${tempoAccessToken}"
              }
          }
          sendGetRequest(url, header, platform, refreshPayload)
      }else{
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

def updateTokens(refreshToken) {

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

  def credentialToUpdate = existingCredential.find { it.id == tempoRefreshToken && it instanceof StringCredentials }

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

node('master') {
  try {
    stage('GetAllTeams'){
      String fetchTeamUrl = "https://api.tempo.io/4/teams"
      withCredentials([
            string(credentialsId: tempoClientId, variable: 'clientId'),
            string(credentialsId: tempoClientSecret, variable: 'clientSecret'),
            string(credentialsId: tempoRefreshToken, variable: 'refreshToken')
      ]){
        def requestHeaders = [[
                                  name: "Authorization",
                                  value: "Bearer ${tempoAccessToken}"
                              ]]
        //prepare payload for refresh token
        def refreshTokenPayload = "grant_type=refresh_token&client_id=${clientId}&client_secret=${clientSecret}&redirect_uri=https://enactor.co/&refresh_token=${refreshToken}"
        println("get teams before access token: ${tempoAccessToken}")
        teamResponse = sendGetRequest(fetchTeamUrl, requestHeaders, "TEMPO", refreshTokenPayload)

      }
    }

    stage('FetchTimeSheets'){
      if(teamResponse){
          withCredentials([
              string(credentialsId: tempoClientId, variable: 'clientId'),
              string(credentialsId: tempoClientSecret, variable: 'clientSecret'),
              string(credentialsId: tempoRefreshToken, variable: 'refreshToken')
            ]){
                mainJSON  = readJSON(text: teamResponse.content)
                //remove properties that are not needed
                mainJSON.remove('self')
                mainJSON.remove('metadata')

                //rename feilds
                mainJSON.teams = json.results
                mainJSON.remove('results')

                mainJSON.teams.each { team ->
                  def teamId = team.id
                  def teamName = team.name

                   //fetched projectId and projectNames
                  println "Team ID: $teamId, team Name: $teamName"

                  String fetchTomeSheetUrl = "https://api.tempo.io/4/timesheet-approvals/team/${teamId}?from=2024-02-26&to=2024-03-03"

                  def requestHeaders = [[
                                      name: "Authorization",
                                      value: "Bearer ${tempoAccessToken}"
                                  ]]
                  def refreshTokenPayload = "grant_type=refresh_token&client_id=${clientId}&client_secret=${clientSecret}&redirect_uri=https://enactor.co/&refresh_token=${refreshToken}"
                  def timeSheetResponse = sendGetRequest(fetchTomeSheetUrl, requestHeaders, "TEMPO", refreshTokenPayload)
                  if(timeSheetResponse){
                      if(timeSheetResponse.status == 200){
                        def timeSheetjson  = readJSON(text: timeSheetResponse.content)
                        //filter apporoved timesheets
                        timeSheetjson.results.each { timesheet ->
                          if(timesheet.status.key == "APPROVED"){
                              //add worklogs to teams JSON
                              team.timesheets = timesheet
                          }
                        }
                      }else{
                            //nothing
                      }
                  }
                } 
                println("Team JSON: ${mainJSON}")              
                
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

