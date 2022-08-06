SELECT
	feedback.appname,
	feedback.appversion,
	feedback.createdAt,
	feedback.device,
	feedback.lang,
	feedback.language,
	feedback.localized,
	feedback.id,
	feedback.modifiedAt,
	feedback.platform,
	feedback.pseudonym,
	feedback.response,
	feedback.status,
	feedback.text,
	feedback.version
FROM
	Feedback feedback
ORDER BY
	feedback.id DESC