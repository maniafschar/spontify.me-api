package com.jq.findapp.service.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class ImportSportsBarService {
	@Autowired
	private Repository repository;

	private static final String URL = "https://skyfinder.sky.de/sf/skyfinder.servlet?detailedSearch=Suchen&group=H&group=B&group=A&country=de&action=search&zip=";

	public SchedulerResult importSportsBars() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/importSportsBars");
		final LocalDateTime now = LocalDateTime.now();
		if (now.getDayOfWeek() == DayOfWeek.SUNDAY && now.getHour() == 4 && now.getMinute() < 10) {
			try {
				;
			} catch (final Exception e) {
				result.exception = e;
			}
		}
		return result;
	}
}
