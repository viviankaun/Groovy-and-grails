package co.wetogether.wco.security

import co.wetogether.wco.app.Admin
import co.wetogether.wco.app.Member
//import co.wetogether.wco.app.Education
import grails.converters.JSON
import grails.transaction.Transactional;
import grails.util.Holders
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.AuthenticationException
import org.apache.shiro.authc.UsernamePasswordToken
import org.apache.shiro.web.util.SavedRequest
import org.apache.shiro.web.util.WebUtils
import org.springframework.http.HttpStatus
import org.scribe.model.*
 

import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;



import static org.springframework.http.HttpStatus.*

class AuthController {
    static responseFormats = ['json']
    static consoleIndexPage = '/console/member'

    def userService

    def index = {
        redirect(action: "login", params: params)
    }

    def login = {
        return [username: params.username, rememberMe: (params.rememberMe != null), targetUri: params.targetUri]
    }

    def signIn = {
        // the header "content-type" here is always "application/x-www-form-urlencoded", therefore withFormat doesn't work
        def outputJson = false
        if (request.getHeader("accept") == "application/json") {
            outputJson = true
        }

        def authToken = new UsernamePasswordToken(params.username, params.password as String)
		def v1=SecurityUtils.subject.principal.toString()
		def v2=SecurityUtils.subject.session.getId().toString()
		log.info " signIn() authToken [$authToken]   for  ${v1}  " 
        if (params.rememberMe) {
            authToken.rememberMe = true
        }

        // If a controller redirected to this page, redirect back to it. Otherwise redirect to the root URI.
        def targetUri = params.targetUri ?: consoleIndexPage

        // Handle requests saved by Shiro filters.
        SavedRequest savedRequest = WebUtils.getSavedRequest(request)
        if (savedRequest) {
            targetUri = savedRequest.requestURI - request.contextPath
            if (savedRequest.queryString) targetUri = targetUri + '?' + savedRequest.queryString
        }

        try {
            SecurityUtils.subject.login(authToken)
			log.info("principal : " + SecurityUtils.subject.principal)
            if (outputJson) {
                def user = WUser.findByUsername(params.username)
				
                // show current member's data
                if (user instanceof Member) {
                    respond user as JSON, [status: OK]
                }
            }
            else {
                loginConsole(targetUri, params)
            }
        }
        catch (AuthenticationException ex) {
            log.info "Authentication failure for user '${params.username}'."
            flash.message = message(code: "login.failed")

            // Keep the username and "remember me" setting so that the user doesn't have to enter them again.
            def m = [username: params.username]
            if (params.rememberMe) {
                m["rememberMe"] = true
            }
            if (params.targetUri) {
                m["targetUri"] = params.targetUri
            }

            if (outputJson) {
                def result = [:]
                result.error = message(code: "login.failed")
                respond result as JSON, [status: UNAUTHORIZED]
            }
            else {
                redirect(action: "login", params: m)
            }
        }
    }
	
	 	
	
	def signIn4APP = { 
		log.info(" signIn4APP() ")
		Token TokenNew = new Token ( params.token as String  ,   ''  ) 
		//run co.wetogether.wco.social.FacebookService
		def providerService = Holders.grailsApplication.mainContext.getBean("${params.provider}Service")
		//run co.wetogether.wco.oauth.SocialToken
		def authToken =providerService.createAuthToken(params.provider,TokenNew) 
	 
		if (!authToken.principal) { //username or email 
			response.status =   400
			render(  [error: " No email in the session for provider '${params.provider}' "] as JSON)
			return 
        }
		try {
			
			def username = authToken.principal
			//find user on DB
			def member = Member.findByUsername(username) 
			
			if (!member){
				def newMember = new Member( 
					email:authToken.principal,
					avatar: providerService.avatarUrl,
					name: providerService.name, 
					socialPlatform: params.provider, 
					enabled: true
				)
				//log.info "create newMember .... "
				newMember.save(failOnError: true) 
			}
			 
			//login to security manager
			SecurityUtils.subject.login authToken			 
			def jsessionid=SecurityUtils.subject.session.getId().toString() 
			member = Member.findByUsername(username)
			member.avatar = providerService.avatarUrl 
			member.jsessionid = jsessionid
			if  (params.provider =='linkedin' ){	 
				member.bio = providerService.headline	
				createExperience(member.id)
			}
			member.save(failOnError: true)
			log.info " signIn4APP for ${username} JSESSIONID=${jsessionid} "
			respond member as JSON , [status: OK]
			
        }
        catch (AuthenticationException ex) {
           log.warn(ex)
        } 
	}
	 
	 
    def signOut = {
		 
		def v1=SecurityUtils.subject.principal.toString() 
		log.info "signOut()  ${v1}   "	 
        SecurityUtils.subject?.logout()
        webRequest.getCurrentRequest().session = null

        request.withFormat {
            html {
                redirect(action: "login")
            }
            json {
                def result = [:]
                result.message = "${v1} signOut successful"
                respond result as JSON, [status: OK]
            }
        }
    }

    def unauthorized = {
        request.withFormat {
            html {
                render view: "/auth/401.gsp"
            }
            json {
                render status: UNAUTHORIZED
            }
        }
    }	
	
    // the activation link on welcome mail comes here
    def activate() {
        if (params.id) {
            def authRequest = EmailAuthRequest.findByToken(params.id)

            if (!authRequest) {
                log.warn "authRequest not found"
                redirect uri: Holders.grailsApplication.config.member.activation.failed as String
            }
            else {
                // the token was issued within 7 days
                def valid = (authRequest.requestDate + 7) >= new Date()

                if (valid) {
                    def member = authRequest.member
                    member.enabled = true
                    member.save(flush: true)
                    authRequest.delete(flush: true)

                    redirect uri: Holders.grailsApplication.config.member.activation.successful as String
                }
                else {
                    log.info "authRequest token expired: ${params.id}"
                    redirect uri: Holders.grailsApplication.config.member.activation.failed as String
                }
            }
        }
    }
	
    // resend the activation email
    def resend() {
        def email = request.JSON.email
        if (email) {
            def member = Member.findByEmail(email)
            member.sendWelcomeMailWithToken()

            def result = [:]
            result.message = "successful"

            request.withFormat {
                '*' {
                    respond result as JSON, [status: OK]
                }
            }
        }
    }

    // ask for a password reset email
    def request() {
        def email = request.JSON.email
        if (email) {
            def member = Member.findByEmail(email)
            def result = [:]

            if (member) {
                member.sendPasswordResetMailWithToken()
                result.message = "successful"

                request.withFormat {
                    '*' {
                        respond result as JSON, [status: OK]
                    }
                }
            }
            else {
                result.message = "member not found"

                request.withFormat {
                    '*' {
                        respond result as JSON, [status: NOT_FOUND]
                    }
                }
            }
        }
    }

    def reset() {
        def password = request.JSON.password
        def token = request.JSON.token

        if (token) {
            def authRequest = EmailAuthRequest.findByToken(token)

            if (!authRequest) {
                def message = "token not found"
                throw new IllegalArgumentException(message)
            }
            else {
                def member = authRequest.member
                member.passwordHash = password
                member.save(flush: true)
                authRequest.delete(flush: true)

                def result = [:]
                result.message = "successful"

                request.withFormat {
                    '*' {
                        respond result as JSON, [status: OK]
                    }
                }
            }
        }
    }

    private def loginConsole(targetUri, params) {
        // only admins can access console
        if (!userService.isCurrentUserAdmin()) {
            redirect(action: "unauthorized")
        }
        else {
            def http = new HTTPBuilder('https://www.google.com')

            // reCAPTCHA
            http.request(Method.POST) {
                uri.path = '/recaptcha/api/siteverify'
                requestContentType = ContentType.URLENC

                body = [
                        secret  : Holders.grailsApplication.config.grails.app.recaptcha.secret,
                        response: params['g-recaptcha-response']
                ]

                response.success = {
                    if (it.statusLine.statusCode.toString() == HttpStatus.OK.toString() && params['g-recaptcha-response']) {
                        redirect(uri: targetUri)
                    }
                    else {
                        redirect(action: "unauthorized")
                    }
                }
            }
        }
    }
}
