pipeline {

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
	    buildDiscarder( logRotator ( artifactDaysToKeepStr:'10',
				    artifactNumToKeepStr:'5',
				    daysToKeepStr:'10',
				    numToKeepStr:'5'
	        )
	    )
    }

    agent { docker {
        image 'docker.seal-software.net/build-agent'
        args dockerArgs()
    }}

    environment {
        BUILDVERSION = buildVersion()
    }

    stages {

        stage('setup') {
        steps {
          printMessage("Setup")
          sh 'mvn --version'
          setupVersion()
        }}

        stage('build') {
          steps {
            printMessage("Building")
            sh "mvn -U clean package"
          }
          post { always {
            // Show test results in jenkins ui
            junit '**/target/surefire-reports/**/*.xml'
          }}
        }

//        stage('deploy') {
//          steps {
//            printMessage("Deploying")
//            deploy()
//          }
//        }
    }

    post {
        always {
            cleanWs()
        }
    }

}

def printMessage(message) {
    def mVersion = getVersion()
    println(message + " version $mVersion")
}

def setupVersion() {
    def mVersion = getVersion()
    sh("mvn -B versions:set -DnewVersion=$mVersion -DgenerateBackupPoms=false")
}

def deploy() {
    def mProfile = getDeployProfile()
    sh("mvn -B -P$mProfile -e clean deploy -Dmaven.test.skip=true")
}

def getVersion() {
    return BUILDVERSION
}

def getDeployProfile() {
    return isRelease() ? "release" : "builds"
}

def isRelease() {
    return env.BRANCH_NAME ==~ /^releases\/.*/
}
