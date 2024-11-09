SELECT
	storage.id,
	storage.createdAt,
	storage.modifiedAt,
	storage.storage,
	storage.label
FROM
	Storage storage
WHERE
	{search}