SELECT
	contactWhatToDo.id,
	contactWhatToDo.active,
	contactWhatToDo.contactId,
	contactWhatToDo.locationId,
	contactWhatToDo.keywords,
	contactWhatToDo.budget,
	contactWhatToDo.time,
	contactWhatToDo.message,
	contactWhatToDo.modifiedAt,
	contactWhatToDo.createdAt,
	(select name from Location location where location.id=contactWhatToDo.locationId and contactWhatToDo.locationId is not null) as locationName
FROM
	ContactWhatToDo contactWhatToDo
WHERE
	contactWhatToDo.contactId={USERID} and
	{search}
ORDER BY
	id DESC