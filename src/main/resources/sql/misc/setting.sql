SELECT
	setting.id,
	setting.label,
	setting.value,
	setting.modifiedAt
FROM
	Setting setting
WHERE
	{search}