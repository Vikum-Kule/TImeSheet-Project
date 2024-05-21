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
String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'

//tokens
tempoAccessToken = null
tempoRefreshToken = 'TEMPO_REFRESH_TOKEN'
tempoAccessTokenCred = 'TEMPO_ACCESS_TOKEN'

Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
def tempoRefreshBody
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
        tempoClientIdCred = lookupSystemCredentials(tempoClientId)
        tempoClientSecretCred = lookupSystemCredentials(tempoClientSecret)
        tempoRefreshTokenCred = lookupSystemCredentials(tempoRefreshToken)
        tempoAccess = lookupSystemCredentials(tempoAccessTokenCred)
        tempoAccessToken = tempoAccess.getSecret()
        tempoRefreshBody = "grant_type=refresh_token&client_id=${tempoClientIdCred.getSecret()}&client_secret=${tempoClientSecretCred.getSecret()}&redirect_uri=https://enactor.co/&refresh_token="
    

        def isNextAvailable = true;
        def temList = []
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
                    if(account.customer){
                        if(account.customer.name.toLowerCase() == Customer.toLowerCase()){
                             temList.add(account.name+ "(" + account.key +")")
                        }
                    }
                }
            }
            // if available next url
            if(accountResult.metadata.next){
              accountUrl = accountResult.metadata.next
            }else{
              isNextAvailable= false
              accountList = temList
            } 
        }
        accountList.add("Token: "+ tempoAccessToken)
  } catch (Exception err) {
    println 'Caught an error while running the build. Saving error log in the database.'
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
      return accountList
  }