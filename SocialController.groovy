package co.wetogether.wco.security

import co.wetogether.wco.app.Member
import co.wetogether.wco.app.Experience
import co.wetogether.wco.util.DateTimeUtil
import grails.transaction.Transactional
import grails.util.Holders
import org.apache.shiro.SecurityUtils
import org.apache.shiro.authc.AuthenticationException
import uk.co.desirableobjects.oauth.scribe.OauthService
import uk.co.desirableobjects.oauth.scribe.holder.RedirectHolder
import grails.converters.JSON

import groovy.sql.Sql
 
 /*
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

import org.springframework.http.HttpStatus.*
*/ 
class SocialController {
	//static responseFormats = ['json']
    OauthService oauthService
	def dataSource
    def index() {}

    /**
     * https://github.com/pledbrook/grails-shiro-oauth/blob/master/grails-app/controllers/grails/plugin/shiro/oauth/ShiroOAuthController.groovy
     */
    @Transactional 
    def login() {
		
        def sessionKey = oauthService.findSessionKeyForAccessToken(params.provider)

        if (!session[sessionKey]) {
            render status: 500, text: "No OAuth token in the session for provider '${params.provider}'."
            return
        }

        def authToken = createAuthToken(params.provider, session[sessionKey])

        if (!authToken.principal) { //username or email
            render status: 500, text: "No email in the session for provider '${params.provider}'."
            return
        }

        try {
            SecurityUtils.subject.login authToken
            def member = Member.findByUsername(SecurityUtils.subject.principal)
            def providerService = Holders.grailsApplication.mainContext.getBean("${params.provider}Service") 
            member.avatar = providerService.avatarUrl
			
			if  (params.provider =='linkedin' ){
				member.bio = providerService.headline	
				createExperience(member.id)
			}
			member.save(failOnError: true)
			 
			log.info " login() authToken [$authToken]    "
			 
	       
            if (RedirectHolder.redirect.uri == '/') {
                // show current user's data
				log.info " /api/members/$member.id "
                RedirectHolder.setUri("/api/members/$member.id" as String)
            }
			 
            //redirect uri: "${RedirectHolder.redirect.uri}/$member.email"
			def frontEndServerURL = Holders.grailsApplication.config.grails.frontEndServerURL
			def serverURL = params.redirectUr ?:  "$frontEndServerURL/m_social_lg_resp/$member.email"
			redirect uri: "$serverURL"
			
			
			//respond member as JSON, [status: 200] 
			
			
        }
        catch (AuthenticationException ex) {
            session["shiroAuthToken"] = authToken
            forward action: "create", params: params
        }
    }

	
    @Transactional
    def create() {
        def providerService = Holders.grailsApplication.mainContext.getBean("${params.provider}Service")
		
        def newMember = new Member(
                email: session["shiroAuthToken"].username,
                avatar: providerService.avatarUrl,
                name: providerService.name,
                socialPlatform: params.provider,
                enabled: true
        )

        try {
            newMember.save(failOnError: true)
			
        }
        catch (e) {
            log.warn(e)
        }

        forward action: "login"
    }
	
	def createExperience( memberId){ 
		def providerService = Holders.grailsApplication.mainContext.getBean("linkedinService")
		def db = new Sql(dataSource)
		def rows
		if ( providerService.endat  ==  null){
			log.info "select id from  experience where member_id=$memberId and start_at= $providerService.startat "
			rows = db.rows  "select id from  experience where member_id=$memberId and start_at= $providerService.startat "
		}else {
			log.info "select id from experience where member_id=$memberId and start_at= $providerService.startat and end_at= $providerService.endat "
			rows = db.rows "select id from  experience where member_id=$memberId and start_at= $providerService.startat and end_at= $providerService.endat "
		}
		
		if (rows.size() > 0) {
			def mid=rows.get(0).get("id")	 
			log.info "delete from experience  where id=$mid"
			db.execute  " delete from experience  where id=$mid" 
		}
		def newExperience = new Experience(
			jobTitle:providerService.title,
			companyName:providerService.company,
			startAt: DateTimeUtil.trimTime(providerService.startat),
			endAt: DateTimeUtil.trimTime(providerService.endat),
			memberId: memberId
		)
		log.info "create newExperience .... "
		newExperience.save(failOnError: true)
		
	}

    protected createAuthToken(providerName, scribeToken) {
        def providerService = Holders.grailsApplication.mainContext.getBean("${providerName}Service")
        return providerService.createAuthToken(providerName, scribeToken)
    }

    protected getProfile(providerName) {
        def providerService = Holders.grailsApplication.mainContext.getBean("${providerName}Service")
        return providerService.profile
    }

}
