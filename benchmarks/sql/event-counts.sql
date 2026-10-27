SELECT count(*) AS events,
       min(created_at) AS first_event,
       max(created_at) AS last_event
FROM events;
