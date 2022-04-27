SELECT
	serverSetting.id,
	serverSetting.label,
	serverSetting.value,
	serverSetting.modifiedAt
FROM
	ServerSetting serverSetting
WHERE
	{search}