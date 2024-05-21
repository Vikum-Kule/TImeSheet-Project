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
String xeroTenantId = 'XERO_TENANT_ID'

xeroAccessCred = 'XERO_ACCESS_TOKEN'
//tokens
xeroAccessToken = null
xeroRefreshToken = 'XERO_REFRESH_TOKEN'

Map stagesMap
String workspacePath
String sourceFolder = 'edtSource'
String sourceFolderPath
String scriptFolderPath
String jobExecutionNode = 'master'
def xeroRefreshBody
customerList = []


def sendGetRequest(url, header, platform,id, refreshTokenPayload){
    def get = new URL("https://api.xero.com/api.xro/2.0/Contacts?ContactStatus=ACTIVE").openConnection();
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
    
        String customerUrl = "https://api.xero.com/api.xro/2.0/Contacts?ContactStatus=ACTIVE"

        def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                              [name: "xero-tenant-id", value: "${xeroTenantIdCred.getSecret()}"], 
                            ]
            
        def customerResponse = sendGetRequest(customerUrl, requestHeaders, "XERO","${xeroTenantIdCred.getSecret()}","${xeroRefreshBody}${xeroRefreshTokenCred.getSecret()}")
            
        def slurper = new groovy.json.JsonSlurper()
        def customerResult = slurper.parseText(customerResponse)
        if(!customerResult.Contacts.isEmpty()){
             customerResult.Contacts.each{customer ->
                 customerList.add(customer.Name)
             }
        }

    customerList.add("Token: "+ xeroAccessToken)
  } catch (Exception err) {
    println 'Caught an error while running the build. Saving error log in the database.'
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    return customerList
  }