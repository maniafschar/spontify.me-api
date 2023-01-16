SELECT
	sum(case when eventRating.rating<26 then 1 else 0 end) as one,
	sum(case when eventRating.rating>25 and eventRating.rating<51 then 1 else 0 end) as two,
	sum(case when eventRating.rating>50 and eventRating.rating<76 then 1 else 0 end) as three,
	sum(case when eventRating.rating>75 then 1 else 0 end) as four,
	(
		select
			concat(er.createdAt,' ',er.rating,' ',er.id)
		from
			EventRating er
		where
			er.locationId={ID} and
			er.contactId={USERID} and
			er.createdAt=(select max(er2.createdAt) from EventRating er2 where er2.locationId={ID} and er2.contactId={USERID})
	) as lastRating
FROM
	EventRating eventRating,
	Contact contact
WHERE
	eventRating.locationId={ID} and
	eventRating.contactId=contact.id and
	{search}
GROUP BY
	eventRating.locationId
ORDER BY
	eventRating.id DESC