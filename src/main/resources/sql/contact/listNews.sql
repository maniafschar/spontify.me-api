SELECT
	contactNews.id,
	contactNews.contactId,
	contactNews.publish,
	contactNews.image,
	contactNews.imgUrl,
	contactNews.url,
	contactNews.createdAt,
	contactNews.modifiedAt,
	contactNews.description,
	contact.pseudonym,
	contact.imageList
FROM
	ContactNews contactNews,
	Contact contact
WHERE
	contactNews.contactId=contact.id and
	contact.clientId={CLIENTID} and
	{search}
ORDER BY
	contactNews.publish DESC