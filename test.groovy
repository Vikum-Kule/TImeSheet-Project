if (Mark_Worklogs_As_Invoiced) {
    def accountsList = Accounts.split(',');
    def pattern = /\((.*?)\)[^(]*$/

    String htmlScript = ""
    accountsList.each{ acc->
        def matcher = (acc =~ pattern)
        if (matcher.find()) {
            def selectedAccountKey = matcher.group(1)
            htmlScript = htmlScript+ "<label for='name'>${acc}:</label><input name=${selectedAccountKey} value='' class='setting-input' type='text'><br>"
        }
    }
    return htmlScript
}