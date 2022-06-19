SELECT
	sum(case when locationRating.rating<26 then 1 else 0 end) as one,
	sum(case when locationRating.rating>25 and locationRating.rating<51 then 1 else 0 end) as two,
	sum(case when locationRating.rating>50 and locationRating.rating<76 then 1 else 0 end) as three,
	sum(case when locationRating.rating>75 then 1 else 0 end) as four,
	(
		select
			concat(lr.createdAt,' ',lr.rating,' ',lr.id)
		from
			LocationRating lr
		where
			lr.locationId={ID} and
			lr.contactId={USERID} and
			lr.createdAt=(select max(lr2.createdAt) from LocationRating lr2 where lr2.locationId={ID} and lr2.contactId={USERID})
	) as lastRating,
	(
		select
			lo.ownerId
		from
			Location lo
		where
			lo.id={ID}
	) as ownerId
FROM
	LocationRating locationRating,
	Contact contact
WHERE
	locationRating.locationId={ID} and
	locationRating.contactId=contact.id and
	{search}