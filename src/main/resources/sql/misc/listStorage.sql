SELECT
	storage.id,
	storage.createdAt,
	storage.storage,
	storage.label
FROM
	Storage storage
WHERE
	{search}