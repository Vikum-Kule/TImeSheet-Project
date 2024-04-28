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
String xeroClientId = 'XERO_CLIENT_ID'
String xeroClientSecret = 'XERO_CLIENT_SECRET'
Boolean isFinalInvoice = params.Final_Invoice_Generation

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

def quoteList
workLogList = []
String tempoRefreshBody
String jiraRefreshBody
def xeroRefreshBody


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

node('master') {
  try {
    stage('Preparation') {
      cleanWs()
      withCredentials([
            string(credentialsId: tempoClientId, variable: 'clientIdTempo'),
            string(credentialsId: tempoClientSecret, variable: 'clientSecretTempo'),
            string(credentialsId: xeroClientId, variable: 'clientIdXero'),
            string(credentialsId: xeroClientSecret, variable: 'clientSecretXero')
      ]){
        //prepare payload for refresh tokens
        tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
        xeroRefreshBody = "grant_type=refresh_token&client_id=${clientIdXero}&client_secret=${clientSecretXero}&refresh_token="
      }
    }

    stage('fetch Invoices'){
        withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero')
            ])
        {
          String fetchInvoicesUrl = "https://api.xero.com/api.xro/2.0/Invoices?Statuses=DRAFT"

                            def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                                        [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                                                      ]
                            def invoicesResponse = sendGetRequest(fetchInvoicesUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
                            if(invoicesResponse.status == 200){
                              def invoicesJSON  = readJSON(text: invoicesResponse.content)
                              println("Fetch Draft Invoices: ${invoicesJSON}")
                            }
        }
    }

    stage('fetch Cost Rates'){
        
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

