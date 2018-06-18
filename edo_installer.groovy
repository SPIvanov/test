def FP = "EDO"
def md5sum = "${env.nexusUrl}.md5".toURL().getText()    // Сохраняем мд5 в перепенную
def name = env.nexusUrl.split('/')[-1]                  // Имя файла архива
def name_folder = env.nexusUrl.split('/')[-1] - '.zip'  // Имя папки для патчей
def PATCHES_PATH = "/Nexus/Patches"                   // Путь к директории, где будут сохраняться устанавливаемые патчи
def GIT_PATH = "./Git" 
def UNPACKED_BUILD_FOLDER = ".${PATCHES_PATH}/${name_folder}"
def recipients = ""
//Добавить проверку \ возвести в верхний регистр   
    switch (envId){
        case 'IFT':
            stand = "CDL"
            recipients = recipients + "Kustov.Y.A@omega.sbrf.ru, Balabanov.M.A@omega.sbrf.ru "
            //recipients = recipients + 'Ivanov.Se.Pa@mail.ca.sbrf.ru, '
            ans_vault_cred = 'ECMvaultkey'
            GITcred = "EDOssh"
            GITbranch = "ift"
            GITurl = 'ssh://git@sbrf-bitbucket.ca.sbrf.ru:7999/ci00373149/ci00372852_edosgo.git'
            GITcreddev = "vhdssh"
            nexus_cred = "nexus_user_cred"  // Jenkins credentialsId для ТУЗ CDL 
            hostvars_cl_ecm = "cluster_ecm"
            hostvars_cl_tw = "cluster_tw"
            hostvars_ecm1 = "sbt-oabp-0750"
            hostvars_tw1 = "sbt-oabp-0751"
        break
        case 'NT':
            recipients = recipients + ""
        break
        case 'PSI':
            stand = "CDP PSI"
            recipients = recipients + 'Ivanov.Se.Pa@mail.ca.sbrf.ru, '
            GITcred = "bitbucket_cred"
            GITbranch = "psi"
            GITurl = 'ssh://git@sbrf-bitbucket.ca.sbrf.ru:7999/ci00373149/ci00372852_edosgo.git'
            nexus_cred = "nexus_user_cred"
            hostvars = "cluster_ecm"
            ans_vault_cred = 'vault_test'
            //ans_vault_var = ''
        break
        case 'PROD':
            stand = "CDP PROD"
            recipients = recipients + 'Ivanov.Se.Pa@mail.ca.sbrf.ru, '
            GITcred = "ecm_bb_user"
            GITbranch = "master"
            GITurl = 'ssh://git@sbrf-bitbucket.ca.sbrf.ru:7999/ci00373149/ci00372852_edosgo.git'
            nexus_cred = "nexus_user_cred"
            hostvars = "cluster_ecm"
            ans_vault_cred = 'vault_test'
            //ans_vault_var = ''
        break
        default: currentBuildResult = "FAILURE"
        break
    }
def header = stand + ' ' + FP

stage_result = "Результаты выполнения этапов установки: "
stage_result = stage_result + "<table cols='2'>"
stage_result = stage_result + "<tr><th style='width:85%'></th><th></th></tr>"
println "###################################"
println "Instalation properties:"
def START_TIME = new Date().format('HH:mm:ss dd-MM-yyyy');
println "Distributiv = ${env.nexusUrl}"
println "Start Time: ${START_TIME}"
println "Need to make backup = $NeedBackUp"
println "Ручные действия = $HandActions"

//вывод полученных при запуске значений
environment = envId
if (envId == 'IFT'){
    echo "ticketid: " + ticketid + ", envId: " + envId + ", artifactid: " + artifactid + ", version: " + version + ", jiraid: " + jiraid + ", groupid: " + groupid + ", nexusUrl: " + nexusUrl
}

timestamps {
currentBuild.result = "SUCCESS"
ansiColor('xterm') {
node ('masterLin'){
    cleanWs()
    sh " mkdir -p ${UNPACKED_BUILD_FOLDER}"
    sh " mkdir -p ${GIT_PATH}"
    // если ссылка на дистрибутив не задана - выходим с ошибкой
    if (nexusUrl=="NOTSET"){
            echo 'Exiting, NOTSET specifyed for not set nexusUrl!'
            currentBuildResult = "FAILURE"
    }
    stage ('Check out Nexus'){
        try {
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: nexus_cred, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh"""
                       cd ${UNPACKED_BUILD_FOLDER}/
                       until ( test -e ${name} )
                       do
                            rm -rf *.zip
                            echo "Download" + ${name} + "from nexus"
                            curl -u '${USERNAME}':'${PASSWORD}' ${env.nexusUrl} -o ${name}
                       done
                       unzip $name
                """
                }
                /*sh"""
                       cd ${UNPACKED_BUILD_FOLDER}/
                       curl ${env.nexusUrl} -o ${name}
                       unzip $name
                """   
                writeFile file: "${UNPACKED_BUILD_FOLDER}/${name}.md5", text: "${md5sum}  ${name}\n" //сохраняем а файл
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: nexus_cred, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh"""
                       cd ${UNPACKED_BUILD_FOLDER}/
                       until ( test -e ${name} ) && ( md5sum -c ${name}.md5 )
                       do
                            rm -rf *.zip
                            echo "Download" + ${name} + "from nexus"
                            curl -u '${USERNAME}':'${PASSWORD}' ${env.nexusUrl} -o ${name}
                       done
                       unzip $name
                """
                } 
                */
            stage_result = stage_result + "<tr><td>Check out Nexus</td><td><font color='green'>SUCCESS</font></td></tr>"
        }
        catch(Exception e) {
            currentBuild.result = "FAILURE"
            stage_result = stage_result + "<tr><td>Check out Nexus</td><td><font color='red'>FAILED</font></td></tr>"
            notifyBuild(currentBuild.result, name, stage_result, recipients, header, envId, stand)
            if (envId == 'IFT'){
                KIT_report_result(currentBuild.result)
            }
            throw e
        }
    }

    stage ('Get config from Git'){
        try{

            //  Перенос с bitbucket ролей в папку с инсталятором
            dir("${GIT_PATH}/installer") {
                git branch: 'rc', credentialsId: GITcred, poll: false, url: 'ssh://git@sbrf-bitbucket.ca.sbrf.ru:7999/ci00373149/ci00372852.git'
            }
            //  Перенос с bitbucket конфигурационных файлов
            dir("${GIT_PATH}/config"){
                git branch: GITbranch, credentialsId: GITcred, url: GITurl
            }
            sh """ pwd;ls -la
                   cp -r ${GIT_PATH}/installer ${UNPACKED_BUILD_FOLDER}
                   cp -r ${GIT_PATH}/config/environments/$envId/* ${UNPACKED_BUILD_FOLDER}/installer
            """

            /*dir("${GIT_PATH}"){
                sh """pwd;ls -la"""
                git branch: 'ift', credentialsId: GITcred, url: 'ssh://git@sbrf-bitbucket.ca.sbrf.ru:7999/ci00373149/ci00372852_edosgo.git'
            }
            dir("${GIT_PATH}/environments/$envId"){ 
                stash includes: 'hosts.yml', name: 'hosts'
                stash includes: 'variables.yml', name: 'variables'
            }    
            dir("${UNPACKED_BUILD_FOLDER}/installer"){
                unstash 'hosts'
                unstash 'variables'
            }

            dir ("${GIT_PATH}/environments/$envId/host_vars/$hostvars_ecm1") {
                stash includes: 'vars.yml', name: 'vars'               
                stash includes: 'vault.yml', name: 'secrets'
            }
            dir("${UNPACKED_BUILD_FOLDER}/installer/host_vars/$hostvars_ecm1"){
                unstash 'vars'
                unstash 'secrets'
            }

            dir ("${GIT_PATH}/environments/$envId/host_vars/$hostvars_tw1") {
                stash includes: 'vars.yml', name: 'vars'               
                stash includes: 'vault.yml', name: 'secrets'
            }
            dir("${UNPACKED_BUILD_FOLDER}/installer/host_vars/$hostvars_tw1"){
                unstash 'vars'
                unstash 'secrets'
            }

            dir ("${GIT_PATH}/environments/$envId/host_vars/cluster_ecm") {
                stash includes: 'vars.yml', name: 'vars'               
                stash includes: 'vault.yml', name: 'secrets'
            }
            dir("${UNPACKED_BUILD_FOLDER}/installer/host_vars/cluster_ecm"){
                unstash 'vars'
                unstash 'secrets'
            }

            dir ("${GIT_PATH}/environments/$envId/host_vars/cluster_tw") {
                stash includes: 'vars.yml', name: 'vars'               
                stash includes: 'vault.yml', name: 'secrets'
            }
            dir("${UNPACKED_BUILD_FOLDER}/installer/host_vars/cluster_tw"){
                unstash 'vars'
                unstash 'secrets'
            }
*/
            stage_result = stage_result + "<tr><td>Get configs from Git</td><td><font color='green'>SUCCESS</font></td></tr>"
        }
        catch(Exception e) {
            currentBuild.result = "FAILURE"
            stage_result = stage_result + "<tr><td>Get configs from Git</td><td><font color='red'>FAILED</font></td></tr>"
            notifyBuild(currentBuild.result, name, stage_result, recipients, header, envId, stand)
            if (envId == 'IFT'){
                KIT_report_result(currentBuild.result)
            }
            throw e            
        }
    }    

    stage ('Mail Notification') {
        try {
           //*
           news = sh ( script: """
                            cd ${UNPACKED_BUILD_FOLDER}/
                            cat readme.md | grep -Pzao "(?s)## Ожидаемые функции.*?^\$" | sed 's/\$/ <br>/'
                            #cat whats.new.md | grep -Pzao "(?s)##*?^\$" | sed 's/\$/ <br>/'
                            """,
                       returnStdout: true
                     ).trim()
           println "News = ${news}"
           notifyStartdeploy(name, recipients, header, envId, stand)  
        if (NeedBackUp == "true") {
            input 'Перед продолжением установки необходимо выполнить бекап БД и настроек. Продолжить?'
        }
            //input 'Перед продолжением установки необходимо выполнить бекап БД и настроек. Продолжить?'
            stage_result = stage_result + "<tr><td>Mail Notification</td><td><font color='green'>SUCCESS</font></td></tr>"
        }
        catch(Exception e) {
            currentBuild.result = "FAILURE"
            stage_result = stage_result + "<tr><td>Mail Notification</td><td><font color='red'>FAILED</font></td></tr>"
            notifyBuild(currentBuild.result, name, stage_result, recipients, header, envId, stand)
            if (envId == 'IFT'){
                KIT_report_result(currentBuild.result)
            }
            throw e              
        }
    }

    stage ("Deploy $envId") {
        try {
            dir("${UNPACKED_BUILD_FOLDER}/installer"){
            withCredentials([file(credentialsId: ans_vault_cred, variable: 'EDOvaultKey_var')]) {  
              sh """
                  export ANSIBLE_FORCE_COLOR=true
                  export ANSIBLE_HOST_KEY_CHECKING=false
                  more hosts.yml
                  ansible-playbook main.yml -i hosts.yml --vault-password-file='${EDOvaultKey_var}' --check
              """
            }
            }
            stage_result = stage_result + "<tr><td>Deploy $envId</td><td><font color='green'>SUCCESS</font></td></tr>"
          //*/  
        }
        catch(Exception e) {
            currentBuild.result = "FAILURE"
            stage_result = stage_result + "<tr><td>Deploy $envId</td><td><font color='red'>FAILED</font></td></tr>"
            notifyBuild(currentBuild.result, name, stage_result, recipients, header, envId, stand)       
            if (envId == 'IFT'){
                KIT_report_result(currentBuild.result)
            }
            throw e               
         }
    }

    stage ('Handing actions') {
        if (HandActions == "true") {
            input 'Для завершения установки необходимо выполнить вручную дополнительные действия в соответсвии с инструкцией. Продолжить?'
        }
    }

    // stage ('Smoke-test IFT') {
    //     try {
    //         withCredentials([file(credentialsId: ans_vault_cred, variable: 'DPAvaultKey_var')]) {
    //             dir("${UNPACKED_BUILD_FOLDER}/installer"){
    //         //withCredentials([file(credentialsId: 'DPAvaultkey', variable: 'DPAvaultKey')]) {
    //             sh """
    //                 export ANSIBLE_HOST_KEY_CHECKING=false
    //                 export ANSIBLE_FORCE_COLOR=true
    //                 ansible-playbook smoke.yml -i hosts.yml --vault-password-file='${DPAvaultKey_var}'
    //             """
    //             }
    //         }
    //         stage_result = stage_result + "<tr><td>Smoke-test $envId</td><td><font color='green'>SUCCESS</font></td></tr>"
    //     }
    //     catch(Exception e) {
    //         currentBuild.result = "UNSTABLE"
    //         stage_result = stage_result + "<tr><td>Smoke-test $envId</td><td><font color='purpule'>Unstable (завершены с ошибкой)</font></td></tr>"
    //         notifyBuild(currentBuild.result, name, stage_result, recipients, header, envId, stand)
    //         if (envId == 'IFT'){
    //             KIT_report_result(currentBuild.result)
    //         }
    //         throw e                    
    //     }
    // }

    //sh """ rm -rf ${workspace_path_linux_slave}/${env.JOB_NAME}/* """
    stage ('Reports'){
        echo "Sending reports"
        notifyBuild(currentBuild.result, name, stage_result, recipients, header, envID, stand)
        if (envId == 'IFT'){KIT_report_result(currentBuild.result)}
    }
}
}
}

// Описание функции "Формирование уведомлений начала установки"
def notifyStartdeploy(String name, String recipients, String stand, String envId, String header ) {
  def subjecttext = header + " | " + envId + " | " + "Патч " + name + " начал устанавливаться "
  def bodytext = header + ". <br>Start time:" + new Date().format("HH:mm:ss") + "<br>" +  "Патч " + name + " начал устанавливаться на стенд " + envId + "." 
      bodytext = bodytext + "<br>" + "nexusUrl: " + env.nexusUrl
      //bodytext = bodytext + "<br>" + "Installation on request ticketid=" + ticketid
      bodytext = bodytext + "<br>" + "Лог установки: " + env.BUILD_URL
      //bodytext = bodytext + "<br>" + "С поставкой приходят изменения: "
      //bodytext = bodytext + "<br>" + "С поставкой приходят изменения:" + news + "<br>"
      //bodytext = bodytext + "<details><summary>Spoiler</summary><p> " + news + " </p></details><br>"
      bodytext = bodytext + "<br>" + "Перед продолжением установки необходимо выполнить бекап БД и настроек. <a href=\"${env.BUILD_URL}input/\">Продолжить?</a>"

  mail body: bodytext,
  charset: 'utf-8', mimeType: 'text/html',
  //replyTo: 'sbt-milenin-ps@mail.ca.sbrf.ru',
  subject: subjecttext, 
  to: recipients
}

// Описание функции "Формирование уведомлений ручных действий"  <a href=\"${env.BUILD_URL}input/\">ссылке</a>
def notifyConfirmdeploy(String name, String recipients, String stand, String envId, String header) {
  def subjecttext = header + " | " + envId + " | " + "Подтверждение ручных действий установки " + name + "."
  def bodytext = "<br>" + "Для завершения установки необходимо выполнить вручную дополнительные действия в соответсвии с инструкцией. <a href=\"${env.BUILD_URL}input/\">Продолжить?</a>"
  mail body: bodytext,
  charset: 'utf-8', mimeType: 'text/html',
  subject: subjecttext,
  to: recipients
}


// Описание функции "Формирование уведомлений статуса установки"
def notifyBuild(String buildStatus  = 'STARTED', String name, String stageResult, String recipients, String stand, String envId, String header) {
  buildStatus =  buildStatus ?: ''
  def subjecttext = ''
  def bodytext = ''
  if (buildStatus == 'FAILURE') {
    subjecttext = 'Status of ' + header + ' - FAILED'
    bodytext = 'Патч ' + name + " не установлен. <br> " //+ "<br>"
    bodytext = bodytext + "<br>" + stageResult
    bodytext = bodytext + "<br> Лог " + env.BUILD_URL
    }
  else if (buildStatus == 'SUCCESS') {
    subjecttext = 'Status of ' + header + ' - SUCCESS'
    bodytext = 'Патч ' + name + " установлен <br> " //+ "<br>" 
    bodytext = bodytext + "<br>" + stageResult
    bodytext = bodytext + "<br> Лог " + env.BUILD_URL
    }
  else if (buildStatus == 'FAILED') {
    subjecttext = 'Status of ' + header + ' - FAILED'
    bodytext = 'Патч ' + name + " не установлен <br> " //+ "<br>" 
    bodytext = bodytext + "<br>" + stageResult
    bodytext = bodytext + "<br> Лог " + env.BUILD_URL
    }
  else if (buildStatus == 'UNSTABLE') {
    subjecttext = 'Status of ' + header + ' - Errors'
    bodytext = '(pipeline): Патч ' + name + " установлен, smoke-тест не пройден <br> " //+ "<br>" 
    bodytext = bodytext + "<br>" + stageResult
    bodytext = bodytext + "<br> Лог " + env.BUILD_URL
    } 
  else if (buildStatus == 'ABORTED') {
    subjecttext = 'Status of ' + header + ' - Abort'
    bodytext = 'Установка патча ' + name + " прервана. <br> " //+ "<br>" 
    bodytext = bodytext + "<br>" + stageResult
    bodytext = bodytext + "<br> Лог " + env.BUILD_URL
    }
  mail body: bodytext,
  charset: 'utf-8', mimeType: 'text/html',
  //replyTo: 'sbt-milenin-ps@mail.ca.sbrf.ru',
  subject: subjecttext, 
  to: recipients
}

def KIT_report_result(result) {
    /*def tempreport = "Отладка интеграции с порталом"
    try {
        if ("$jiraid" == 'NOTSET') {
            echo "ticketid: " + ticketid +", currentBuild.result: " + currentBuild.result + ", envId: " + envId + ", nexusUrl: " + nexusUrl
            //build job: 'OPIR/SRV/SPIntegrated', parameters: [string(name: 'ticketid', value: ticketid), string(name: 'result', value: currentBuild.result)] //это шаг возвращает на портал результаты
            build job: 'OPIR/SRV/SPIntegrated', parameters: [string(name: 'ticketid', value: ticketid), string(name: 'result', value: tempreport)] //это шаг возвращает на портал результаты
            echo "KIT 1.0 report success"
        } else {
            echo "ticketid: " + ticketid + ", environment: " + environment + ", artifactid: " + artifactid + ", version: " + version + ", jiraid: " + jiraid + ", groupid: " + groupid + ", result: " + currentBuild.result + ", build: " + currentBuild.number.toString() + ", nexusUrl: " + nexusUrl
            build job: 'OPIR/SRV/SPCloseTicket', parameters: [string(name: 'groupid', value: groupid), 
            string(name: 'artifactid', value: artifactid), 
            string(name: 'version', value: version), 
            string(name: 'nexusUrl', value: nexusUrl), 
            string(name: 'jiraid', value: jiraid),// список jiraid из pom.xml
            string(name: 'result', value: currentBuild.result), // обычно currentBuild.result
            string(name: 'build', value: currentBuild.number.toString()), // номер билда обычно значение из currentBuild.number.toString()
            string(name: 'environment', value: environment), // куда устанавливали ST / IFT /LT 
            string(name: 'ticketid', value: ticketid)] // если закрываете заявку то подставьте значение переменной ticketid, иначе 1 или ничего
    
            echo "KIT 2.0 report success"
        }
    } 
    catch (e) {
        //message = 'mail_result_not_send'
        //notifyBuild(message)
        throw e
    } 
*/
}
