PORTNAME=		sms-forwarder
PORTVERSION=	        1.0.0
CATEGORIES=		net
MASTER_SITES=	        https://github.com/definitly486/SMS-Forwarder-LAN/archive/refs/heads/
DISTFILES=		main.zip

MAINTAINER=		you@example.com
COMMENT=		SMS Forwarder LAN server for FreeBSD
WWW=			https://example.com

LICENSE=		MIT

RUN_DEPENDS=	${PYTHON_PKGNAMEPREFIX}flask>=0:www/py-flask@${PY_FLAVOR} \
				${PYTHON_PKGNAMEPREFIX}flask-cors>=0:www/py-flask-cors@${PY_FLAVOR}

USES=			python:3.9+
USE_RC_SUBR=	smsforwarder

NO_ARCH=		yes
NO_BUILD=		yes

do-install:
	${MKDIR} ${STAGEDIR}${PREFIX}/sms-forwarder
	${INSTALL_DATA} ${FILESDIR}/server.py ${STAGEDIR}${PREFIX}/sms-forwarder/
	${INSTALL_DATA} ${FILESDIR}/database.py ${STAGEDIR}${PREFIX}/sms-forwarder/
	${INSTALL_DATA} ${FILESDIR}/config.json ${STAGEDIR}${PREFIX}/sms-forwarder/
	${INSTALL_DATA} ${FILESDIR}/requirements.txt ${STAGEDIR}${PREFIX}/sms-forwarder/

.include <bsd.port.mk>
