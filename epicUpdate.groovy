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

def quotes = (params.QUOTES_NOT_IN_JIRA == null) ? "" : params.QUOTES_NOT_IN_JIRA
def quoteList
def customerList
String tempoRefreshBody
String jiraRefreshBody
def xeroRefreshBody
def quoteKeyList = []
def accResponseJSON


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

def sendUpdateRequest(url, header, platform, refreshTokenPayload) {
  def response = null
     withCredentials([
            string(credentialsId: tempoAccessCred, variable: 'tokenTempo')
    ]){
      if(platform == "TEMPO"){
        header.each { data ->
                if (data.name == "Authorization") {
                    data.value = "Bearer ${tokenTempo}"
                }
            }
      }else{
      }

      response = httpRequest(url: url,
                             customHeaders: header ,
                             httpMode: 'PUT',
                             validResponseCodes: '200, 401,403, 404')
      // Check if the request was successful or not
      if (response.status == 401){
          if (platform == "TEMPO"){
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
            string(credentialsId: jiraClientId, variable: 'clientIdJira'),
            string(credentialsId: jiraClientSecret, variable: 'clientSecretJira'),
            string(credentialsId: jiraCode, variable: 'codeJira'),
            string(credentialsId: xeroClientId, variable: 'clientIdXero'),
            string(credentialsId: xeroClientSecret, variable: 'clientSecretXero')
      ]){
        //prepare payload for refresh tokens
        tempoRefreshBody = "grant_type=refresh_token&client_id=${clientIdTempo}&client_secret=${clientSecretTempo}&redirect_uri=https://enactor.co/&refresh_token="
        jiraRefreshBody = "grant_type=refresh_token&client_id=${clientIdJira}&client_secret=${clientSecretJira}&code=${codeJira}&redirect_uri=https://enactor.co/&refresh_token="
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
                if(quoteList == null){
                  quoteList = quoteJSON
                }else{
                  quoteList.Quotes.add(quoteJSON.Quotes[0])
                }
            }
          }
          println("Quote List: ${quoteList}")
        }
    }

    stage('Get Categories'){
      if(!quoteList.isEmpty()){
            withCredentials([
                string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
              ]){
                def categoryFetchUrl = "https://api.tempo.io/4/account-categories"
                def requestHeaders = [[
                                      name: "Authorization",
                                      value: "Bearer ${tempoAccessToken}"
                                  ]]
                  def categoryResponse = sendGetRequest(categoryFetchUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )
                  if(categoryResponse){
                      if(categoryResponse.status == 200){
                        categoryList  = readJSON(text: categoryResponse.content)
                        println("Category List : ${categoryList}")
                        quoteList.Quotes.each{quote ->
                            String summary = quote.Summary
                            def pattern = /\{(.+?)\}/
                            def matcher = (summary =~ pattern)
                            boolean isCategoryExist = false
                            quote.categoryKey = null
                            matcher.each { match ->
                                def categoryInSummary = match[1]
                                println("Category curly braces: $categoryInSummary")

                                categoryList.results.each{category ->
                                  if(category.name == categoryInSummary){
                                    quote.categoryKey = category.key
                                    isCategoryExist = true 
                                  } 
                                }
                                if(isCategoryExist){
                                  println("$categoryInSummary Category is not exist in tempo")
                                }
                            }
                        }
                        
                      }
                  } 
            }
      }

    }

    stage('fetch Customers'){
      if(!quoteList.isEmpty()){
            withCredentials([
                string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
              ]){
                def customerFetchUrl = "https://api.tempo.io/4/customers"
                def requestHeaders = [[
                                      name: "Authorization",
                                      value: "Bearer ${tempoAccessToken}"
                                  ]]
                  def customerResponse = sendGetRequest(customerFetchUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )
                  if(customerResponse){
                      if(customerResponse.status == 200){
                        customerList  = readJSON(text: customerResponse.content)
                        println("curtomer List : ${customerList}")
                      }
                  } 
            }
      }
    }

    stage("Fetching Leads"){
      if(!quoteList.isEmpty()){
          withCredentials([
              string(credentialsId: xeroRefreshToken, variable: 'refreshTokenXero'),
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ])
        {
          quoteList.Quotes.each{quote ->
            String fetchCustomerUrl = "https://api.xero.com/api.xro/2.0/contacts/?where=Name%3D%22${quote.Contact.Name}%22&page=0"

            def requestHeaders = [[name: "Authorization", value: "Bearer ${xeroAccessToken}"],
                                  [name: "xero-tenant-id", value: "8652e9a4-0afe-40b5-8c25-a52da8287fb2"],
                                ]
            def customerResponse = sendGetRequest(fetchCustomerUrl, requestHeaders, "XERO","${xeroRefreshBody}${refreshTokenXero}")
            if(customerResponse.status == 200){
              def customerData  = readJSON(text: customerResponse.content)
              
              if(!customerData.isEmpty()){
                def leadName = "${customerData.Contacts[0].ContactPersons[0].FirstName}+${customerData.Contacts[0].ContactPersons[0].LastName}"
                def userUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/user/search?query=${leadName}"

                def jiraRequestHeaders = [[
                        name: "Authorization",
                        value: "Bearer ${jiraAccessToken}"
                    ]]
                def userResponse = sendGetRequest(userUrl, jiraRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}" )
                if(userResponse.status == 200){
                  def userJson  = readJSON(text: userResponse.content)
                  quote.leadId = userJson[0].accountId
                  quote.leadContact = customerData.Contacts[0].ContactPersons[0].FirstName+ " " + customerData.Contacts[0].ContactPersons[0].LastName
                }
              }
            }
          }
        }
    
      }
    }

    stage('Create Accounts'){
        if(!quoteList.Quotes.isEmpty()){
            withCredentials([
                string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
              ]){
                  quoteList.Quotes.each{quote ->
                    def accountStructure = '''{
                                                "key": "",
                                                "leadAccountId": "",
                                                "name": "",
                                                "global": true,
                                                "status": "OPEN",
                                                "customerKey": "",
                                                "categoryKey": "AC501",
                                                "externalContactName": ""
                                              }'''
                    def accountJSON  = readJSON(text: accountStructure)
                    if(quote.categoryKey != null){
                      accountJSON.categoryKey = quote.categoryKey
                    }
                    accountJSON.name = quote.Title
                    accountJSON.key = quote.QuoteNumber
                    accountJSON.leadAccountId = quote.leadId
                    accountJSON.externalContactName = quote.leadContact
                    def accountCustomer  = customerList.results.findAll{customer->
                      customer.name.toLowerCase() == quote.Contact.Name.toLowerCase()
                    }
                    if(!accountCustomer.isEmpty()){
                      accountJSON.customerKey = accountCustomer[0].key
                    }
                    def finalAccount = JsonOutput.prettyPrint(accountJSON.toString())
                    println("final account: ${finalAccount}")
                    String createAccountUrl = "https://api.tempo.io/4/accounts"

                      def requestHeaders = [[
                                          name: "Authorization",
                                          value: "Bearer ${tempoAccessToken}"
                                      ]]
                      def accountResponse = sendPostRequest( createAccountUrl, finalAccount, requestHeaders, "TEMPO", "${tempoRefreshBody}${refreshTokenTempo}")
                      if(accountResponse){
                          if(accountResponse.status == 200){
                            accResponseJSON  = readJSON(text: accountResponse.content)
                            quote.account = accResponseJSON
                          }
                      }
                  }
                }
          } 
    }

    stage('Create Filter'){
      if(!quoteList.Quotes.isEmpty()){
          withCredentials([
                string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
              ])
          {
            quoteList.Quotes.each{quote ->
              if(quote.account){
                def filterStructure = '''
                                 {
                                   "name": "",
                                   "description": "Automated Filter Creating",
                                   "jql": "",
                                   "favourite": true
                                 }          
                                '''
                def filterJson  = readJSON(text: filterStructure)
                filterJson.name = quote.QuoteNumber + " - Filter_testingBudget"
                filterJson.jql = "account.key = " + quote.QuoteNumber
                def finalFilter = JsonOutput.prettyPrint(filterJson.toString())

                def filterUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/filter"

                def jiraRequestHeaders = [[
                        name: "Authorization",
                        value: "Bearer ${jiraAccessToken}"
                    ]]
                def filterResponse = sendPostRequest( filterUrl, finalFilter, jiraRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}")
                if(filterResponse.status == 200 || filterResponse.status == 201){
                  def filterResponseJson  = readJSON(text: filterResponse.content)
                  println("Response Filter: ${filterResponseJson}")
                  quote.filterId = filterResponseJson.id
                }
              }
            }
          } 
      }
    }

    stage('Create Cost Tracker Projects'){
          if(!quoteList.Quotes.isEmpty()){
            withCredentials([
                string(credentialsId: tempoRefreshToken, variable: 'refreshTokenTempo')
              ])
            {
              quoteList.Quotes.each{quote ->
                if(quote.account){
                  //create cost base project
                  def projectBody = """
                                    {
                                      "name": "",
                                      "type": "TIME_BASED",
                                      "scope": {
                                        "reference": "",
                                        "type": "filter"
                                      }
                                    }
                                    """
                  def projectJson  = readJSON(text: projectBody)
                  projectJson.scope.reference = quote.filterId

                  projectJson.name = quote.QuoteNumber + " : " + quote.Contact.Name +" : "+ quote.Title + " - Time"
                  def finalProject_time = JsonOutput.prettyPrint(projectJson.toString())
                  println("Final Cost Proj: "+ finalProject_time )
                  
                  projectJson.currencyCode = quote.CurrencyCode
                  projectJson.type = "MONETARY_BASED"
                  projectJson.defaultCostRate = 0.00
                  projectJson.name = quote.QuoteNumber + " : " + quote.Contact.Name +" : " + quote.Title + " - Cost"
                  def finalProject_cost = JsonOutput.prettyPrint(projectJson.toString())
                  println("Final Cost Proj: "+ finalProject_cost )
                  

                  def costProjectId = null
                  def timeProjectId = null 
                  def projectUrl = "https://api.tempo.io/cost-tracker/1/projects";

                  def requestHeaders = [[
                                          name: "Authorization",
                                          value: "Bearer ${tempoAccessToken}"
                                      ]]
                  def projectResponse_cost = sendPostRequest(projectUrl, finalProject_cost, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}")
                  if(projectResponse_cost.status == 200 || projectResponse_cost.status == 201){
                      println("Cost response: ${projectResponse_cost.content}")
                      def costProjectJSON  = readJSON(text: projectResponse_cost.content)
                      costProjectId = costProjectJSON.id
                  }
                  def projectResponse_time = sendPostRequest(projectUrl, finalProject_time, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}")
                  if(projectResponse_time.status == 200 || projectResponse_time.status == 201){
                      println("Time response: ${projectResponse_time.content}")
                      def timeProjectJSON  = readJSON(text: projectResponse_time.content)
                      timeProjectId = timeProjectJSON.id
                  }

                  // update created projects with budgets
                  if(costProjectId != null || timeProjectId != null){
                      def costBudget =  quote.SubTotal
                      def timeBudget = 0.0
                      quote.LineItems.each{ item ->
                        timeBudget += item.Quantity
                      } 

                      println("Final budget: ${costBudget} and ${timeBudget}")
                      def costBudgetUrl = "https://api.tempo.io/cost-tracker/1/projects/${costProjectId}/budget/${costBudget}"
                      def timeBudgeturl = "https://api.tempo.io/cost-tracker/1/projects/${timeProjectId}/budget/${timeBudget}"

                      def budgetResponse_cost = sendUpdateRequest(costBudgetUrl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )
                      if(budgetResponse_cost.status == 200){
                          println("Budget Updated In Cost Project")
                      }else{
                        println("Budget Can Not Be Updated In Cost Project")
                      }
                      def budgetResponse_time = sendUpdateRequest(timeBudgeturl, requestHeaders, "TEMPO","${tempoRefreshBody}${refreshTokenTempo}" )
                      if(budgetResponse_time.status == 200){
                          println("Budget Updated In Time Project")
                      }else{
                        println("Budget Can Not Be Updated In Time Project")
                      }
                  
                  }else{
                    println("there is a problem with project creating")
                  }
                }
              }
            }
          }
    }

    stage('Create Tasks'){
      if(!quoteList.Quotes.isEmpty()){
          withCredentials([
              string(credentialsId: jiraRefreshToken, variable: 'refreshTokenJira')
            ]){
            quoteList.Quotes.each{quote ->
                  if(!quote.LineItems.isEmpty() && quote.account){
                      quote.LineItems.each{ item ->
                          def issueStructure = '''
                                                {
                                                  "fields": {
                                                    "project":
                                                    {
                                                        "key": "TIWI",
                                                    },
                                                    "customfield_12382": "",
                                                    "summary": "",
                                                    "description": {
                                                      "content": [
                                                        {
                                                          "content": [
                                                            {
                                                              "text": "",
                                                              "type": "text"
                                                            }
                                                          ],
                                                          "type": "paragraph"
                                                        }
                                                      ],
                                                      "type": "doc",
                                                      "version": 1
                                                    },
                                                    "assignee":{
                                                        "accountId":""
                                                    },
                                                    "timetracking": {
                                                        "originalEstimate": "",
                                                        "remainingEstimate": ""
                                                    },
                                                    "components": [
                                                      {
                                                        "id": "16107"
                                                      }
                                                    ],
                                                    "issuetype": {
                                                        "name": "Epic",
                                                    }
                                                  }
                                                }
                                                '''
                          def issueJson  = readJSON(text: issueStructure)
                          issueJson.fields.summary = item.ItemCode
                          issueJson.fields.assignee.accountId = null
                          issueJson.fields.customfield_12382 = quote.account.id
                          issueJson.fields.timetracking.originalEstimate = item.Quantity
                          issueJson.fields.timetracking.remainingEstimate = item.Quantity
                          issueJson.fields.description.content[0].content[0].text = item.ItemCode
                          def finalIssue = JsonOutput.prettyPrint(issueJson.toString())
                          println("finalIssue : ${finalIssue}") 
                          def issueUrl = "https://api.atlassian.com/ex/jira/2eafded6-d1b9-41bd-8b84-6600f92e0032/rest/api/3/issue"

                          def jiraRequestHeaders = [[
                                  name: "Authorization",
                                  value: "Bearer ${jiraAccessToken}"
                              ]]
                         def issueResponse = sendPostRequest( issueUrl, finalIssue, jiraRequestHeaders, "JIRA", "${jiraRefreshBody}${refreshTokenJira}")
                          if(issueResponse.status == 200 || issueResponse.status == 201){
                            def issueResponseJson  = readJSON(text: issueResponse.content)
                            println("Response Issue: ${issueResponseJson}")
                          }    
                      } 
                }
            }
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

