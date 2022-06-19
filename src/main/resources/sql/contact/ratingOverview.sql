SELECT
	sum(case when contactRating.rating<26 then 1 else 0 end) as one,
	sum(case when contactRating.rating>25 and contactRating.rating<51 then 1 else 0 end) as two,
	sum(case when contactRating.rating>50 and contactRating.rating<76 then 1 else 0 end) as three,
	sum(case when contactRating.rating>75 then 1 else 0 end) as four,
	(
		select
			concat(cr.createdAt,' ',cr.rating,' ',cr.id)
		from
			ContactRating cr
		where
			cr.contactId2={ID} and
			cr.contactId={USERID} and
			cr.createdAt=(select max(cr2.createdAt) from ContactRating cr2 where cr2.contactId2={ID} and cr2.contactId={USERID})
	) as lastRating,
	(
		select
			case when contactLink.id>0 then 1 else 0 end
		from
			ContactLink contactLink
		where
			contactLink.status='Friends' and
			(
				contactLink.contactId={USERID} and contactLink.contactId2={ID} or
				contactLink.contactId2={USERID} and	contactLink.contactId={ID}
			)
	) as contactLink
FROM
	ContactRating contactRating,
	Contact contact
WHERE
	contactRating.contactId2={ID} and
	contact.id=contactRating.contactId and
	{search}