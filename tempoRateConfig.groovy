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
tempoAccessCred = 'TEMPO_ACCESS_TOKEN'
xeroAccessCred = 'XERO_ACCESS_TOKEN'
jiraAccessCred = 'JIRA_ACCESS_TOKEN'
String tempoClientId = 'TEMPO_CLIENT_ID'
String tempoClientSecret = 'TEMPO_CLIENT_SECRET'
String tempoCode = 'TEMPO_CODE'
String jiraClientId = 'JIRA_CLIENT_ID'
String jiraClientSecret = 'JIRA_CLIENT_SECRET'
String jiraCode = 'JIRA_CODE' 
String xeroClientId = 'XERO_CLIENT_ID'
String xeroClientSecret = 'XERO_CLIENT_SECRET'

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

def quotes = (params.QUOTES_FOR_COST_RATE_UPDATE == null) ? "" : params.QUOTES_FOR_COST_RATE_UPDATE

def quoteList = []
String tempoRefreshBody
String jiraRefreshBody
def xeroRefreshBody
def tempoRoles = null
def quoteKeyList = []


def sendGetRequest(url, header, platform, refreshTokenPayload) {
  def response = null
     withCredentials([
            string(credentialsId: tempoAccessCred, variable: 'tokenTempo'),
            string(credentialsId: xeroAccessCred, variable: 'tokenXero'),
            string(credentialsId: jiraAccessCred, variable: 'tokenJira'),
    ]){

      if(platform == "TEMPO"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenTempo}"
                }
            }
      }else if(platform == "JIRA"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenJira}"
                }
            }
      }else if(platform == "XERO"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenXero}"
                }
            }
      }else{

      }

      response = httpRequest(url: url,
                             customHeaders: header ,
                             httpMode: 'GET',
                             validResponseCodes: '200, 401,403, 404')
      // Check if the request was successful or not
      if (response.status == 401){
          if (platform == "TEMPO"){
              refreshTokens(platform, refreshTokenPayload)
              sendGetRequest(url, header, platform, refreshTokenPayload)
          }else if (platform == "JIRA"){
              refreshTokens(platform, refreshTokenPayload)
              sendGetRequest(url, header, platform, refreshTokenPayload)

          }else if(platform == "XERO"){
              refreshTokens(platform, refreshTokenPayload)
              sendGetRequest(url, header, platform, refreshTokenPayload)

          }
        }else{
          println(response)
          return response
        }   
    }
}

def sendPostRequest( url, payload, header, platform, refreshTokenPayload) {  
    def response = null
    withCredentials([
            string(credentialsId: tempoAccessCred, variable: 'tokenTempo'),
            string(credentialsId: xeroAccessCred, variable: 'tokenXero'),
            string(credentialsId: jiraAccessCred, variable: 'tokenJira'),
    ]){

      if(platform == "TEMPO"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenTempo}"
                }
            }
      }else if(platform == "JIRA"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenJira}"
                }
            }
      }else if(platform == "XERO"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenXero}"
                }
            }
      }else{
      }
        response = httpRequest acceptType: 'APPLICATION_JSON',
               contentType: 'APPLICATION_JSON',
               customHeaders: header,
               httpMode: 'POST',
               requestBody: payload,
               consoleLogResponseBody: true,
               validResponseCodes: '200, 201, 401, 403, 404',
               url: url
    if (response.status == 401){
      if(platform == "XERO"){
          refreshTokens(platform, refreshTokenPayload)
          sendPostRequest( url, payload, header, platform, refreshTokenPayload)
      }else if (platform == "JIRA"){
          refreshTokens(platform, refreshTokenPayload)
          sendPostRequest( url, payload, header, platform, refreshTokenPayload)
      }else if (platform == "TEMPO"){
          refreshTokens(platform, refreshTokenPayload)
          sendPostRequest( url, payload, header, platform, refreshTokenPayload)
      }else{
      }
    }else{
      return response
    }
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
          updateTokens(tempoAccessToken, tempoAccessCred)
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
          updateTokens(jiraAccessToken, jiraAccessCred)
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
          updateTokens(xeroAccessToken, xeroAccessCred)
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

node('release && linux') {
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

        if(quotes != ""){
          def quotesList = quotes.split(',');
          def pattern = /\((.*?)\)[^(]*$/
          
          quotesList.each{ q->
            def matcher = (q =~ pattern)
            if (matcher.find()) {
                def selectedQuoteKey = matcher.group(1)
                quoteKeyList.add(selectedQuoteKey)
            }
          }

          println("Account key: ${quoteKeyList}")
      }else{
        currentBuild.result = 'ABORTED'
        error('Select At Least One Quote from Quote List')
      }
      
      }
    }

    stage('Fetch Quotes'){
      withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero')
            ])
        {
          quoteKeyList.each{key ->
            String fetchQuoteUrl = "https://api.xero.com/api.xro/2.0/Quotes?QuoteNumber=${key}"

            def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                  [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                                ]
            def quoteResponse = sendGetRequest(fetchQuoteUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
            if(quoteResponse.status == 200){
                def quoteJSON = readJSON(text: quoteResponse.content)
                println("quoteJSON: ${quoteJSON}")
                quoteList.add(quoteJSON.Quotes[0])
            }
          }
          println("Quote List: ${quoteList}")
        }
    }

    stage('Fetch Tempo Roles'){
       withCredentials([
               string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
             ])
         {
           String roleUrl = "https://api.tempo.io/4/roles"

           def requestHeaders = [[
                                    name: "Authorization",
                                    value: "Bearer ${tempoAccessToken}"
                                ]]
           def roleResponse = sendGetRequest(roleUrl, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")
           if(roleResponse.status == 200){
               roleJSON = readJSON(text: roleResponse.content)
               tempoRoles = roleJSON.results
                println("List Of Roles: ${tempoRoles}")
           }
         }
    }

    stage('CostTracking'){
       if(!quoteList.isEmpty()){
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
            quoteList.each{ quote ->
                def costProject = costJSON.results.findAll{project->
                                project.name == "${quote.QuoteNumber} : ${quote.Title} - Cost"
                           }
                if(!costProject.isEmpty()){
                  println("costProject[0].id: ${costProject[0]}")
                  quote.projectId = costProject[0].id

                  //fetch rates
                  String rateUrl = costProject[0].rates.self
                  String roleUrl = costProject[0].roles.self
                  def rateRequestHeaders = [[
                                        name: "Authorization",
                                        value: "Bearer ${tempoAccessToken}"
                                        ]]

                  roleResponse = sendGetRequest(roleUrl, rateRequestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
                  if(roleResponse.status == 200){
                    def roleJSON = readJSON(text: roleResponse.content) 
                    roleJSON.roles.each{ role->
                        println("role.role.id : ${role.role.id}")
                        def filteredRole = tempoRoles.findAll{tempoRole ->
                          tempoRole.id == role.role.id
                        }

                        println("Filtered role : ${filteredRole} and ${role}")
                        if(!filteredRole.isEmpty()){
                          role.name = filteredRole[0].name
                        }else{
                          role.name = "Member"
                        }
                    }

                    println("Filtered roles : ${roleJSON.roles}")
                    if(!roleJSON.roles.isEmpty()){
                      rateResponse = sendGetRequest(rateUrl, rateRequestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}" )
                      
                      if(rateResponse.status == 200){
                          def rateJSON = readJSON(text: rateResponse.content)
                          rateJSON.rates.each{projectRate ->
                            def fileteredRole = roleJSON.roles.findAll{role ->
                              role.teamMember.id == projectRate.teamMember.id
                            }
                            projectRate.roleName = fileteredRole[0].name
                          }
                          println("rateJSON.rates : ${rateJSON.rates}")
                          quote.rateData = rateJSON.rates
                      }
                    }  
                 }
                }else{
                      println("Not match project")
                        
                }
            }
            

           }

         }
       }
    }

    stage('Update Cost Project'){
      if(!quoteList.isEmpty()){
         withCredentials([
               string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
             ])
         {
            quoteList.each{ q->
              def projectId = q.projectId

              Calendar cal = Calendar.getInstance()
              cal.add(Calendar.DAY_OF_MONTH, 0)
              SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd")
              String today = sdf.format(cal.getTime())


              q.LineItems.each{item ->
                def updateStructure = '''
                                    {
                                      "rate": "",
                                      "category": "COST",
                                      "effectiveDate": ""
                                    }
                                    '''
                def updateJSON  = readJSON(text: updateStructure)
                // assign effective date as today
                updateJSON.effectiveDate = today
                
                boolean isRoleMatched = false
                q.rateData.each{data ->
                  println("role name: ${data.roleName} and item description: ${item.ItemCode} ")
                  if(data.roleName == item.ItemCode){
                    isRoleMatched = true
                    updateJSON.rate = data.
                  }
                }
                if(!isRoleMatched){
                  currentBuild.result = 'ABORTED'
                  error("${item.ItemCode} Role in QUOTE: ${q.QuoteNumber} Not Matched With Cost Project's Roles ")
                }

              }
           }

         }
      }
    }

    currentBuild.result = 'SUCCESS'
  } catch (Exception err) {
    echo err.toString()
    currentBuild.result = 'FAILURE'
    throw err
  } finally {
    action = 'FINISH_LAUNCHING'
    cleanWs()
  }
}

