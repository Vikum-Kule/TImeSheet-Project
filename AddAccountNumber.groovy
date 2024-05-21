if (Mark_Worklogs_As_Invoiced) {
    def accountsList = Accounts.split(',');
    def pattern = /\((.*?)\)[^(]*$/

    String htmlScript = ""
    accountsList.each{ acc->
        def matcher = (acc =~ pattern)
        if (matcher.find()) {
            def selectedAccountKey = matcher.group(1)
            htmlScript = htmlScript+ "<input name='value' value='${acc}: ' class='setting-input' type='text'><br><br>"
        }
    }
    return htmlScript
}