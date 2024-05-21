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
// import groovyx.net.http.RESTClient

import hudson.security.ACL

/*===================== External method loads =================== */
def svnHelper
def pipelineHelper
//credentials 
String xeroClientId = 'XERO_CLIENT_ID'
String xeroClientSecret = 'XERO_CLIENT_SECRET'

String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'
String xeroTenantId = 'XERO_TENANT_ID'

xeroAccessCred = 'XERO_ACCESS_TOKEN'
//tokens
xeroAccessToken = null
xeroRefreshToken = 'XERO_REFRESH_TOKEN'

tempoAccessToken = null
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
tempoAccessTokenCred = 'TEMPO_ACCESS_TOKEN'


Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
String jobExecutionNode = 'master'
def xeroRefreshBody
def tempoRefreshBody

qouteList = []
accountList = []


def sendPostRequest(url,payload, refreshTokenPayload ){
  def accpost = new URL(url).openConnection();
    def message = payload
    accpost.setRequestMethod("POST")
    accpost.setDoOutput(true)
    accpost.setRequestProperty("Content-Type", "application/json")
    accpost.setRequestProperty("Authorization", "Bearer ${tempoAccessToken}")
    accpost.getOutputStream().write(message.getBytes("UTF-8"));
    def accpostRC = accpost.getResponseCode();
    if (accpostRC.equals(401)) {
        refreshTempoTokens("TEMPO", refreshTokenPayload)
        sendPostRequest(url,payload, refreshTokenPayload)
    }else if(accpostRC.equals(200)){
        def accountResponse = accpost.getInputStream().getText()
        return accountResponse
    }else{
        accountList.add("Account fetch error")
    }

}


def sendGetRequest(url, header, platform,id, refreshTokenPayload){
    def get = new URL(url).openConnection();
    get.setRequestProperty("Content-Type", "application/json")
    get.setRequestProperty("Authorization", "Bearer ${xeroAccessToken}")
    get.addRequestProperty("xero-tenant-id", id)
    def getRC = get.getResponseCode();
    if (getRC.equals(401)) {
        refreshTokens(platform, refreshTokenPayload)
        sendGetRequest(url, header, platform,id, refreshTokenPayload)
    }else if(getRC.equals(200)){
        def customerResponse = get.getInputStream().getText()
        return customerResponse
    }else{
        customerList.add("Customer fetch error")
    }

}

def refreshTokens(platform, refreshTokenPayload){
    switch (platform) {
      case "XERO":
          println "Xero Refresh Token Update"
          String xeroRefreshUrl = 'https://identity.xero.com/connect/token?='
          def xeroTokenPayload = refreshTokensRequest(xeroRefreshUrl, refreshTokenPayload )
          // save on disk
        //   def jsonResponse = readJSON text: xeroTokenPayload.content
          def slurper = new groovy.json.JsonSlurper()
          def result = slurper.parseText(xeroTokenPayload)
          xeroAccessToken = result.access_token
          def refreshToken = result.refresh_token
          updateTokens(refreshToken, xeroRefreshToken)
          updateTokens(xeroAccessToken, xeroAccessCred)
          break
       case "TEMPO":
          String tempoRefreshUrl = 'https://api.tempo.io/oauth/token/?grant_type=&client_id=&client_secret=&refresh_token'
          def tempoTokenPayload = refreshTempoTokensRequest(tempoRefreshUrl, refreshTokenPayload )
          // save on disk
          def slurper = new groovy.json.JsonSlurper()
          def result = slurper.parseText(tempoTokenPayload)
          tempoAccessToken = result.access_token
          def refreshToken = result.refresh_token
          updateTempoTokens(refreshToken, tempoRefreshToken)
          updateTempoTokens(tempoAccessToken, tempoAccessTokenCred)
          break
      default:
          println "No match found."
  }
}

def refreshTokensRequest (url, payload ){
  def post = new URL(url).openConnection();
    def message = payload
    post.setRequestMethod("POST")
    post.setDoOutput(true)
    post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    post.getOutputStream().write(message.getBytes("UTF-8"));
    def postRC = post.getResponseCode();
    if (postRC.equals(200)) {
        def tokensString = post.getInputStream().getText()
        return tokensString
    }

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
  } else {
  }
}

def refreshTempoTokens(platform, refreshTokenPayload){
    switch (platform) {
      case "TEMPO":
          String tempoRefreshUrl = 'https://api.tempo.io/oauth/token/?grant_type=&client_id=&client_secret=&refresh_token'
          def tempoTokenPayload = refreshTempoTokensRequest(tempoRefreshUrl, refreshTokenPayload )
          // save on disk
          def slurper = new groovy.json.JsonSlurper()
          def result = slurper.parseText(tempoTokenPayload)
          tempoAccessToken = result.access_token
          def refreshToken = result.refresh_token
          updateTempoTokens(refreshToken, tempoRefreshToken)
          updateTempoTokens(tempoAccessToken, tempoAccessTokenCred)
          break
      default:
          println "No match found."
  }
}

def refreshTempoTokensRequest (url, payload ){
  def post1 = new URL(url).openConnection();
    def message = payload
    post1.setRequestMethod("POST")
    post1.setDoOutput(true)
    post1.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    post1.getOutputStream().write(message.getBytes("UTF-8"));
    def postRC1 = post1.getResponseCode();
    if (postRC1.equals(200)) {
        def tokensString = post1.getInputStream().getText()
        return tokensString
    }else{
    }

}

def updateTempoTokens(refreshToken, credentialId) {
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
  } else {
  }
}

  try {

        jenkins = Jenkins.get()
        def lookupSystemCredentials = { credentialsId ->
            return CredentialsMatchers.firstOrNull(
                CredentialsProvider
                .lookupCredentials(com.cloudbees.plugins.credentials.Credentials.class, jenkins, ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
            )
        }

        xeroClientIdCred = lookupSystemCredentials(xeroClientId)
        xeroClientSecretCred = lookupSystemCredentials(xeroClientSecret)
        xeroRefreshTokenCred = lookupSystemCredentials(xeroRefreshToken)
        xeroAccess = lookupSystemCredentials(xeroAccessCred)
        xeroTenantIdCred = lookupSystemCredentials(xeroTenantId)
        JIRARefreshTokenCred = lookupSystemCredentials('JIRA_ACCESS_TOKEN')
        jiraAccess = JIRARefreshTokenCred.getSecret()
        xeroAccessToken = xeroAccess.getSecret()
        xeroRefreshBody = "grant_type=refresh_token&client_id=${xeroClientIdCred.getSecret()}&client_secret=${xeroClientSecretCred.getSecret()}&refresh_token="
        

        tempoClientIdCred = lookupSystemCredentials(tempoClientId)
        tempoClientSecretCred = lookupSystemCredentials(tempoClientSecret)
        tempoRefreshTokenCred = lookupSystemCredentials(tempoRefreshToken)
        tempoAccess = lookupSystemCredentials(tempoAccessTokenCred)
        tempoAccessToken = tempoAccess.getSecret()
        tempoRefreshBody = "grant_type=refresh_token&client_id=${tempoClientIdCred.getSecret()}&client_secret=${tempoClientSecretCred.getSecret()}&redirect_uri=https://enactor.co/&refresh_token="
    

         
        def isNextAvailable = true;
        def quoteMap = [:]
        def accountUrl = "https://api.tempo.io/4/accounts/search"
        while(isNextAvailable){
            def accountBody = '''
                           {
                             "keys": [],
                             "statuses": [
                               "OPEN"
                             ]
                           }
                         '''
                         

            def accountResponse = sendPostRequest(accountUrl,accountBody,"${tempoRefreshBody}${tempoRefreshTokenCred.getSecret()}")
    
             
            
            //filter according to customer
            def slurper = new groovy.json.JsonSlurper()
            def accountResult = slurper.parseText(accountResponse)
            

            if(!accountResult.results.isEmpty()){
                accountResult.results.each{account ->
                     quoteMap["${account.key}"] = ""
                }
            }
            
            // if available next url
            if(accountResult.metadata.next){
              accountUrl = accountResult.metadata.next
            }else{
              isNextAvailable= false
             
            } 
        }    

        String quoteUrl = "https://api.xero.com/api.xro/2.0/Quotes?Status=ACCEPTED"

        def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                              [name: "xero-tenant-id", value: "${xeroTenantIdCred.getSecret()}"], 
                            ]

        def quoteResponse = sendGetRequest(quoteUrl , requestHeaders, "XERO","${xeroTenantIdCred.getSecret()}","${xeroRefreshBody}${xeroRefreshTokenCred.getSecret()}")


                
        def slurper = new groovy.json.JsonSlurper()
        def quoteResult = slurper.parseText(quoteResponse)
        
        if(!quoteResult.Quotes.isEmpty()){
             quoteResult.Quotes.each{qoute->
                if (quoteMap.count{it -> it.key == qoute.QuoteNumber} != 0) {
                    qouteList.add(qoute.Title + " (" + qoute.QuoteNumber + ")")
                }
             }
        }

    // qouteList.add("Token: "+jiraAccess)
  } catch (Exception err) {
    println 'Caught an error while running the build. Saving error log in the database.'
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    return qouteList 
  }