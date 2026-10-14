https://gipvwromtsqosgmvzamq.supabase.co    
 sb_publishable_FzQs7qBbx1HiFatsMC421Q_lCtn63oV
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/fetch-oddsmath
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-betexplorer
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-betwatch
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-fbref
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-oddschecker
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-oddspedia
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-oddsportal
 
 https://gipvwromtsqosgmvzamq.supabase.co/functions/v1/scrape-understat
 
 à adapter pour notre application
 API
 
 
 --------------------------------------------------
---------------------------------------------------
 https://dashboard.api-football.com/soccer/widgets

<api-sports-widget data-type="leagues"></api-sports-widget>

<!-- Configuration -->
<api-sports-widget data-type="config"
  data-key="<voir local.properties : API_SPORTS_WIDGET_KEY>"
  data-sport="football"
  data-lang="fr"
  data-theme="white"
  data-show-errors="true"
></api-sports-widget>


---------------------------------------------------
---------------------------------------------------
https://rapidapi.com/boggio-analytics/api/football-prediction/playground/Predictions

{
  "mcpServers": {
    "RapidAPI Hub - Football Prediction": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "https://mcp.rapidapi.com",
        "--header",
        "x-api-host: football-prediction-api.p.rapidapi.com",
        "--header",
        "x-api-key: <voir local.properties : RAPIDAPI_KEY>"
      ]
    }
  }
}



val client = OkHttpClient()

val request = Request.Builder()
	.url("https://football-prediction-api.p.rapidapi.com/api/v2/predictions?market=classic&iso_date=2018-12-01&federation=UEFA")
	.get()
	.addHeader("x-rapidapi-key", "<voir local.properties : RAPIDAPI_KEY>")
	.addHeader("x-rapidapi-host", "football-prediction-api.p.rapidapi.com")
	.build()

val response = client.newCall(request).execute()


---------------------------------------------------
---------------------------------------------------
https://api.sportmonks.com/v3/football/fixtures/19722157?include=participants;league;venue;state;scores;events.type;events.period;events.player;xGFixture.type;lineups.player;lineups.xGlineup.type;lineups.details.type

https://my.sportmonks.com/api/playground
https://api.sportmonks.com/v3/football/livescores/latest?api_token=YOUR_TOKEN
tocken: <voir local.properties : SPORTMONKS_TOKEN>
https://api.sportmonks.com/v3/football/statistics/seasons/teams/85?api_token=YOUR_TOKEN&include=team;details.type&filters=teamStatisticSeasons:26740
https://api.sportmonks.com/v3/football/statistics/seasons/{participant}/{participantID}?api_token=YOUR_TOKEN&include=team;season;player;position;coach;referee&locale=fr


repose exemple
{
  "data": [
    {
      "id": 427906,
      "team_id": 85,
      "season_id": 26740,
      "has_values": true,
      "team": {
        "id": 85,
        "sport_id": 1,
        "country_id": 320,
        "venue_id": 5655,
        "gender": "male",
        "name": "FC København",
        "short_code": "COP",
        "image_path": "https://cdn.sportmonks.com/images/soccer/teams/21/85.png",
        "founded": 1992,
        "type": "domestic",
        "placeholder": false,
        "last_played_at": "2026-10-04 13:30:00"
      },
      "details": [
        {
          "id": 6089281,
          "team_statistic_id": 427906,
          "type_id": 34,
          "value": {
            "count": 31,
            "average": 3.88
          },
          "type": {
            "id": 34,
            "name": "Corners",
            "code": "corners",
            "developer_name": "CORNERS",
            "model_type": "statistic",
            "stat_group": "offensive"
          }
        },
        {
          "id": 6089305,
          "team_statistic_id": 427906,
          "type_id": 45,
          "value": {
            "count": 368,
            "average": 46
          },
          "type": {
            "id": 45,
            "name": "Ball Possession %",
            "code": "ball-possession",
            "developer_name": "BALL_POSSESSION",
            "model_type": "statistic",
            "stat_group": "overall"
          }
        },
        {
          "id": 6089299,
          "team_statistic_id": 427906,
          "type_id": 52,
          "value": {
            "all": {
              "count": 19,
              "average": 2.38,
              "first_goal": 4
            },
            "home": {
              "count": 17,
              "percentage": 89.47,
              "average": 3.4,
              "first_goal": 4
            },
            "away": {
              "count": 2,
              "percentage": 10.53,
              "average": 0.67,
              "first_goal": 51
            }
          },
          "type": {
            "id": 52,
            "name": "Goals",
            "code": "goals",
            "developer_name": "GOALS",
            "model_type": "statistic",
            "stat_group": "offensive"
          }
        },
        {
          "id": 6089306,
          "team_statistic_id": 427906,
          "type_id": 83,
          "value": {
            "count": 1,
            "average": 0.13,
            "player_id": 26867,
            "player_name": "Jordan Larsson",
            "coach": 0,
            "coach_average": 0
          },
          "type": {
            "id": 83,
            "name": "Redcards",
            "code": "redcards",
            "developer_name": "REDCARDS",
            "model_type": "statistic",
            "stat_group": "overall"
          }
        },
        {
          "id": 6103038,
          "team_statistic_id": 427906,
          "type_id": 84,
          "value": {
            "count": 7,
            "average": 0.88,
            "player_id": 33677,
            "player_name": "Thomas Delaney",
            "coach": 0,
            "coach_average": 0
          },
          "type": {
            "id": 84,
            "name": "Yellowcards",
            "code": "yellowcards",
            "developer_name": "YELLOWCARDS",
            "model_type": "statistic",
            "stat_group": "overall"
          }
        },
        {
          "id": 6089297,
          "team_statistic_id": 427906,
          "type_id": 88,
          "value": {
            "all": {
              "count": 12,
              "average": 1.5,
              "first": 8
            },
            "home": {
              "count": 8,
              "percentage": 66.67,
              "average": 1.6,
              "first": 8
            },
            "away": {
              "count": 4,
              "percentage": 33.33,
              "average": 1.33,
              "first": 8
            }
          },
          "type": {
            "id": 88,
            "name": "Goals Conceded",
            "code": "goals-conceded",
            "developer_name": "GOALS_CONCEDED",
            "model_type": "statistic",
            "stat_group": "defensive"
          }
        },
        {
          "id": 6089304,
          "team_statistic_id": 427906,
          "type_id": 191,
          "value": {
            "over_0_5": {
              "matches": {
                "count": 8,
                "percentage": 100
              },
              "team": {
                "count": 7,
                "percentage": 87.5
              }
            },
            "over_1_5": {
              "matches": {
                "count": 8,
                "percentage": 100
              },
              "team": {
                "count": 5,
                "percentage": 62.5
              }
            },
            "over_2_5": {
              "matches": {
                "count": 5,
                "percentage": 62.5
              },
              "team": {
                "count": 4,
                "percentage": 50
              }
            },
            "over_3_5": {
              "matches": {
                "count": 4,
                "percentage": 50
              },
              "team": {
                "count": 2,
                "percentage": 25
              }
            },
            "over_4_5": {
              "matches": {
                "count": 3,
                "percentage": 37.5
              },
              "team": {
                "count": 1,
                "percentage": 12.5
              }
            },
            "over_5_5": {
              "matches": {
                "count": 1,
                "percentage": 12.5
              },
              "team": {
                "count": 0,
                "percentage": 0
              }
            }
          },
          "type": {
            "id": 191,
            "name": "Number Of Goals",
            "code": "number-of-goals",
            "developer_name": "NUMBER_OF_GOALS",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089278,
          "team_statistic_id": 427906,
          "type_id": 192,
          "value": {
            "all": {
              "count": 6,
              "percentage": 75
            },
            "home": {
              "count": 4,
              "percentage": 66.67
            },
            "away": {
              "count": 2,
              "percentage": 33.33
            }
          },
          "type": {
            "id": 192,
            "name": "Both Teams To Score",
            "code": "btts",
            "developer_name": "BTTS",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089280,
          "team_statistic_id": 427906,
          "type_id": 194,
          "value": {
            "all": {
              "count": 1,
              "percentage": 12.5
            },
            "home": {
              "count": 1,
              "percentage": 100
            },
            "away": {
              "count": 0,
              "percentage": 0
            }
          },
          "type": {
            "id": 194,
            "name": "Cleansheets",
            "code": "cleansheets",
            "developer_name": "CLEANSHEET",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089308,
          "team_statistic_id": 427906,
          "type_id": 196,
          "value": {
            "0-15": {
              "count": 1,
              "percentage": 6.67
            },
            "15-30": {
              "count": 1,
              "percentage": 6.67
            },
            "30-45": {
              "count": 5,
              "percentage": 33.33
            },
            "45-60": {
              "count": 3,
              "percentage": 20
            },
            "60-75": {
              "count": 3,
              "percentage": 20
            },
            "75-90": {
              "count": 2,
              "percentage": 13.33
            }
          },
          "type": {
            "id": 196,
            "name": "Scoring Minutes",
            "code": "scoring-minutes",
            "developer_name": "SCORING_MINUTES",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089298,
          "team_statistic_id": 427906,
          "type_id": 213,
          "value": {
            "0-15": {
              "count": 5,
              "percentage": 41.67
            },
            "15-30": {
              "count": 1,
              "percentage": 8.33
            },
            "30-45": {
              "count": 0,
              "percentage": 0
            },
            "45-60": {
              "count": 2,
              "percentage": 16.67
            },
            "60-75": {
              "count": 0,
              "percentage": 0
            },
            "75-90": {
              "count": 4,
              "percentage": 33.33
            }
          },
          "type": {
            "id": 213,
            "name": "Conceded Scoring Minutes",
            "code": "conceded-scoring-minutes",
            "developer_name": "CONCEDED_SCORING_MINUTES",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089311,
          "team_statistic_id": 427906,
          "type_id": 214,
          "value": {
            "all": {
              "count": 5,
              "percentage": 62.5
            },
            "home": {
              "count": 5,
              "percentage": 100
            },
            "away": {
              "count": 0,
              "percentage": 0
            }
          },
          "type": {
            "id": 214,
            "name": "Team Wins",
            "code": "team-wins",
            "developer_name": "WIN",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089285,
          "team_statistic_id": 427906,
          "type_id": 215,
          "value": {
            "all": {
              "count": 2,
              "percentage": 25
            },
            "home": {
              "count": 0,
              "percentage": 0
            },
            "away": {
              "count": 2,
              "percentage": 100
            }
          },
          "type": {
            "id": 215,
            "name": "Team Draws",
            "code": "team-draws",
            "developer_name": "DRAW",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089300,
          "team_statistic_id": 427906,
          "type_id": 216,
          "value": {
            "all": {
              "count": 1,
              "percentage": 12.5
            },
            "home": {
              "count": 0,
              "percentage": 0
            },
            "away": {
              "count": 1,
              "percentage": 100
            }
          },
          "type": {
            "id": 216,
            "name": "Team Lost",
            "code": "team-lost",
            "developer_name": "LOST",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089287,
          "team_statistic_id": 427906,
          "type_id": 575,
          "value": {
            "all": {
              "count": 1,
              "percentage": 12.5
            },
            "home": {
              "count": 0,
              "percentage": 0,
              "overall_percentage": 0
            },
            "away": {
              "count": 1,
              "percentage": 100,
              "overall_percentage": 12.5
            }
          },
          "type": {
            "id": 575,
            "name": "Failed To Score",
            "code": "failed-toscore",
            "developer_name": "FAILED_TO_SCORE",
            "model_type": "statistic",
            "stat_group": "offensive"
          }
        },
        {
          "id": 6453418,
          "team_statistic_id": 427906,
          "type_id": 9672,
          "value": {
            "avg_defender_height": 181.22,
            "avg_midfielder_height": 182.57,
            "avg_goalkeeper_height": 191.5,
            "avg_attacker_height": 183.75,
            "avg_total_height": 183.05
          },
          "type": {
            "id": 9672,
            "name": "Average Player Height",
            "code": "average-player-height",
            "developer_name": "AVERAGE_PLAYER_HEIGHT",
            "model_type": "statistic",
            "stat_group": "overall"
          }
        },
        {
          "id": 6089310,
          "team_statistic_id": 427906,
          "type_id": 27249,
          "value": {
            "total_minutes_played": 720
          },
          "type": {
            "id": 27249,
            "name": "Total Minutes Played",
            "code": "total-minutes-played",
            "developer_name": "TOTAL_MINUTES_PLAYED",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089302,
          "team_statistic_id": 427906,
          "type_id": 27250,
          "value": {
            "most_scored_half": "2nd-half",
            "most_scored_half_goals": 14,
            "details": {
              "2nd-half": {
                "period": "2nd-half",
                "total": 14
              },
              "1st-half": {
                "period": "1st-half",
                "total": 13
              }
            }
          },
          "type": {
            "id": 27250,
            "name": "Most Scored Half",
            "code": "most-scored-half",
            "developer_name": "MOST_SCORED_HALF",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6103034,
          "team_statistic_id": 427906,
          "type_id": 27256,
          "value": {
            "won_both_halves": 1,
            "scored_both_halves": 2,
            "comebacks": 2
          },
          "type": {
            "id": 27256,
            "name": "Half Results",
            "code": "half-results",
            "developer_name": "HALF_RESULTS",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089303,
          "team_statistic_id": 427906,
          "type_id": 27258,
          "value": {
            "national_team_players": [
              {
                "team": "Czech Republic",
                "player": 82032
              },
              {
                "team": "Peru",
                "player": 259454
              },
              {
                "team": "Denmark U20",
                "player": 37609216
              },
              {
                "team": "Denmark U21",
                "player": 37609216
              },
              {
                "team": "Japan",
                "player": 37616318
              },
              {
                "team": "South Africa",
                "player": 37618713
              },
              {
                "team": "Iceland U19",
                "player": 37702659
              },
              {
                "team": "Iceland U21",
                "player": 37702659
              },
              {
                "team": "Hungary",
                "player": 37722503
              },
              {
                "team": "Hungary U20",
                "player": 37722503
              },
              {
                "team": "Hungary U21",
                "player": 37722503
              },
              {
                "team": "Argentina U20",
                "player": 37729625
              }
            ]
          },
          "type": {
            "id": 27258,
            "name": "National Team Players",
            "code": "national-team-players",
            "developer_name": "NATIONAL_TEAM_PLAYERS",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6127055,
          "team_statistic_id": 427906,
          "type_id": 27260,
          "value": {
            "total": 3,
            "average": 0.38
          },
          "type": {
            "id": 27260,
            "name": "Injury Time Goals",
            "code": "injury-time-goals",
            "developer_name": "INJURY_TIME_GOALS",
            "model_type": "statistic",
            "stat_group": null
          }
        },
        {
          "id": 6089293,
          "team_statistic_id": 427906,
          "type_id": 27263,
          "value": {
            "total": 8,
            "home": 5,
            "away": 3
          },
          "type": {
            "id": 27263,
            "name": "Games Played",
            "code": "games-played",
            "developer_name": "GAMES_PLAYED",
            "model_type": "statistic",
            "stat_group": null
          }
        }
      ]
    }
  ],
  "pagination": {
    "count": 1,
    "per_page": 50,
    "has_more": false,
    "current_page": 1,
    "next_page": null,
    "next_cursor": null
  },
  "subscription": [
    {
      "meta": [],
      "plans": [
        {
          "plan": "Cricket Free Plan",
          "sport": "Cricket",
          "category": "Standard"
        },
        {
          "plan": "Football Free Plan",
          "sport": "Football",
          "category": "Standard"
        }
      ],
      "add_ons": [],
      "widgets": []
    }
  ],
  "rate_limit": {
    "resets_in_seconds": 3600,
    "remaining": 179,
    "requested_entity": "TeamStatistic"
  },
  "timezone": "UTC"
}


---------------------------------------------------
---------------------------------------------------
https://www.thesportsdb.com/docs_api_examples

function fetchJSONData() {
            fetch("https://www.thesportsdb.com/api/v1/json/123/searchteams.php?t=Arsenal")
                .then((res) => {
                    if (!res.ok) {
                        throw new Error
                            (`HTTP error! Status: ${res.status}`);
                    }
                    return res.json();
                })
                .then((data) => 
                      console.log(data))
                .catch((error) => 
                       console.error("Unable to fetch data:", error));
        }
        fetchJSONData();
		


var fbURLv2="https://www.thesportsdb.com/api/v2/json/all/leagues";
var commentdata = "";
$.ajax({
    url: fbURLv2,
    data: "message="+commentdata,
    dataType: "json",
    type: 'POST',
    beforeSend: function(xhr) {
        xhr.setRequestHeader('X-API-KEY', 'xxxxxx');
        xhr.setRequestHeader('Content-Type', 'application/json');
    },
    // If success         
    success: function (resp) {
        console.log(resp);
    },
    // If error
    error: function(e) {
        console.log(e);
    }
});

---------------------------------------------------
---------------------------------------------------