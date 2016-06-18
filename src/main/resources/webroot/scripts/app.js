//Each of the controllers should be moved to separate files for easy readability 
//In the end they will be assembled as a single js file using requireJS and grunt build system
(function($) {
	//Setup dependencies for the module
	var app = angular.module('mysocial', [ 'ngRoute','textAngular','ngWebsocket']);
	app.run(function($http,$rootScope,$location,$log,$websocket) {
		$log.debug("App run...");
		$rootScope.currentPath = $location.path()
	});

	//ROUTE configurations for all views
	app.config([ '$routeProvider', function($routeProvider) {
		$routeProvider.when('/', {
			templateUrl : 'templates/appHome.html',
			controller : 'AppHomeController'
		}).when('/login', {
			templateUrl : 'templates/login.html',
			controller : 'LoginController'
		}).when('/register', {
			templateUrl : 'templates/register.html',
			controller : 'LoginController'
		}).when('/newPost', {
			templateUrl : 'templates/BlogEdit.html',
			controller : 'BlogController'
		}).when('/logOut', {
			templateUrl : 'templates/login.html',
			controller : 'LoginController'
		}).otherwise({
			templateUrl : '/404.html'
		});
	} ]).factory('authHttpResponseInterceptor',
			[ '$q', '$location','$log', function($q, $location, $log) {
				return {
					request: function (config) {
                    config.headers = config.headers || {};
                    if (localStorage.getItem("token") != null) {
                        config.headers.Authorization = 'Bearer ' + localStorage.getItem('token');
                    }
					else {
						$location.path('/login');
					}
                    return config;
					},
/* 					response : function(response) {
						if (response.status === 401) {
							$log.debug("Response 401");
						}
						return response || $q.when(response);
					}, */
					responseError : function(rejection) {
						if (rejection.status === 401) {
							$log.debug("Response Error 401", rejection);
							$location.path('/login');
						}
						return $q.reject(rejection);
					}
				}
			} ]).config([ '$httpProvider', function($httpProvider) {
		// Http Intercpetor to check auth failures for xhr requests
		$httpProvider.interceptors.push('authHttpResponseInterceptor');
	} ]);

	//------------------------------------------------------------------------------------------------------------------
	// Controller for the home page with blogs and live users
	//------------------------------------------------------------------------------------------------------------------
	app.controller('AppHomeController', function($http, $log, $scope,
			$rootScope, $websocket, $location) {
		if ($rootScope.loggedIn == false) {
			$location.path("/login");
		}
		var controller = this;
		$log.debug("AppHomeController...");
		$http.get('/Services/rest/blogs').success(
				function(data, status, headers, config) {
					$scope.blogs = data;
					$scope.loading = false;
				}).error(function(data, status, headers, config) {
					$scope.loading = false;
					$scope.error = status;
				});
		var ws=null;
		$http.get('/Services/rest/user?signedIn=true').success(
				function(data, status, headers, config) {
					$scope.connectedUsers = data;
					$scope.loading = false;
					//Setup a websocket connection to server using current host
					ws = $websocket.$new('ws://'+$location.host()+':'+$location.port()+'/Services/chat', ['binary', 'base64']); // instance of ngWebsocket, handled by $websocket service
					$log.debug("Web socket established...");
			        ws.$on('$open', function () {
			            $log.debug('Socket is open');
			        });
			        
			        ws.$on('$message', function(data){
			        	 $log.debug('The websocket server has sent the following data:');
			        	 $log.debug(data);
			        	 $log.debug(data.messageType);
			        	 if(data.messageType==="UserLogin"){
			        		 //Add this user to list of users
			        		 var found = false;
			        		 for(var index in $scope.connectedUsers){
			        			 if($scope.connectedUsers[index].id==data.messageObject.id){
			        				 found=true;
			        			 }
			        		 }
			        		 if(!found){
			        			 $log.debug("Adding user to list: "+data.messageObject.first);
			        			 $scope.connectedUsers.push(data.messageObject);
			        			 $scope.$digest();
			        		 }
			        	 }else if(data.messageType==="chatMessage"){
			        		 //Make sure chat window opensup
			        		 $scope.showChat=true
			        		 $log.debug("Updating chat message: ");
			        		 $log.debug(data.messageObject);
			        		 if($scope.chatMessages===undefined)
			        			 $scope.chatMessages=[];
			        		 $scope.chatMessages.push(data.messageObject);
			        		 $log.debug("Chat Messages: ");
			        		 $log.debug($scope.chatMessages);
			        		 $scope.$digest();
			        	 }
			        });
			        ws.$on('$close', function () {
			            console.log('Web socket closed');
			            ws.$close();
			        });
				}).error(function(data, status, headers, config) {
					$scope.loading = false;
					$scope.error = status;
				});
			$scope.tagSearch = function(){
				$http.get('/Services/rest/blogs?tag='+$scope.searchTag).success(
					function(data, status, headers, config) {
						$scope.blogs = data;
						$scope.loading = false;
					}).error(function(data, status, headers, config) {
						$scope.loading = false;
						$scope.error = status;
					});
			};
			$scope.submitComment = function(comment, blogId){
				$log.debug(comment);
				//var blogId = comment.blogId;
				$http.post('/Services/rest/blogs/'+blogId+'/comments',comment).success(
					function(data, status, headers, config) {
						$scope.loading = false;
						for(var index in $scope.blogs){
							if($scope.blogs[index].id==blogId){
								$log.debug("Pushing the added comment to list");
								$scope.blogs[index].comments.push(comment);
								break;
							}
						}
					}).error(function(data, status, headers, config) {
						$scope.loading = false;
						$scope.error = status;
					});
			};
		
			$scope.sendMessage = function(chatMessage){
				$log.debug("Sending "+chatMessage);
				ws.$emit('chatMessage', chatMessage); // send a message to the websocket server
				$scope.chatMessage="";
			}
	});
	//------------------------------------------------------------------------------------------------------------------
	// Controller for the login view and the registration screen
	//------------------------------------------------------------------------------------------------------------------
	app.controller('LoginController',function($http, $log,  $location,$scope,
			$rootScope) {
		var controller = this;
		$scope.isLoadingCompanies = true;
//		var Base64={_keyStr:"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",encode:function(e){var t="";var n,r,i,s,o,u,a;var f=0;e=Base64._utf8_encode(e);while(f<e.length){n=e.charCodeAt(f++);r=e.charCodeAt(f++);i=e.charCodeAt(f++);s=n>>2;o=(n&3)<<4|r>>4;u=(r&15)<<2|i>>6;a=i&63;if(isNaN(r)){u=a=64}else if(isNaN(i)){a=64}t=t+this._keyStr.charAt(s)+this._keyStr.charAt(o)+this._keyStr.charAt(u)+this._keyStr.charAt(a)}return t},decode:function(e){var t="";var n,r,i;var s,o,u,a;var f=0;e=e.replace(/[^A-Za-z0-9+/=]/g,"");while(f<e.length){s=this._keyStr.indexOf(e.charAt(f++));o=this._keyStr.indexOf(e.charAt(f++));u=this._keyStr.indexOf(e.charAt(f++));a=this._keyStr.indexOf(e.charAt(f++));n=s<<2|o>>4;r=(o&15)<<4|u>>2;i=(u&3)<<6|a;t=t+String.fromCharCode(n);if(u!=64){t=t+String.fromCharCode(r)}if(a!=64){t=t+String.fromCharCode(i)}}t=Base64._utf8_decode(t);return t},_utf8_encode:function(e){e=e.replace(/rn/g,"n");var t="";for(var n=0;n<e.length;n++){var r=e.charCodeAt(n);if(r<128){t+=String.fromCharCode(r)}else if(r>127&&r<2048){t+=String.fromCharCode(r>>6|192);t+=String.fromCharCode(r&63|128)}else{t+=String.fromCharCode(r>>12|224);t+=String.fromCharCode(r>>6&63|128);t+=String.fromCharCode(r&63|128)}}return t},_utf8_decode:function(e){var t="";var n=0;var r=c1=c2=0;while(n<e.length){r=e.charCodeAt(n);if(r<128){t+=String.fromCharCode(r);n++}else if(r>191&&r<224){c2=e.charCodeAt(n+1);t+=String.fromCharCode((r&31)<<6|c2&63);n+=2}else{c2=e.charCodeAt(n+1);c3=e.charCodeAt(n+2);t+=String.fromCharCode((r&15)<<12|(c2&63)<<6|c3&63);n+=3}}return t}}

		$scope.login = function(user) {
			$log.debug("Logging in user...");
			$http.post("/Services/rest/user/auth", user).success(
					function(data) {
						
						localStorage.setItem('token', data.token);
						$rootScope.loggedIn = true;
						$location.path("/");
					});
		};
		$scope.register = function() {
			$log.debug("Navigating to register...");
			$location.path("/register");
	 		$http.get('/Services/rest/company').success(
				function(data, status, headers, config) {
					$scope.companies = data;
					$scope.isLoadingCompanies = false;
					$location.path("/login");
				}).error(function(data, status, headers, config) {
					$scope.isLoadingCompanies = false;
					$scope.error = status;
				});		
		};
		$scope.submitRegister = function(user){
			$log.debug("Registering...");
			$http.post("/Services/rest/user/register", user).success(
					function(data) {
						$log.debug(data);
						localStorage.setItem('token', data.token);
						$rootScope.loggedIn = true;
						$location.path("/");
					});
		}
		$scope.companyChange = function(companyId) {
			$log.debug("Loading sites for company: " + companyId);
			// Load sites
			$http.get('/Services/rest/company/'+companyId+'/sites').success(
					function(data, status, headers, config) {
						$scope.sites = data;
						$scope.isLoadingSites = false;
					}).error(function(data, status, headers, config) {
						$scope.isLoadingSites = false;
						$scope.error = status;
					});
		};
		
		$scope.siteChange = function(companyId, siteId) {
			$log.debug("Loading departments: " + companyId);
			// Load sites
			$http.get('/Services/rest/company/'+companyId+'/sites/'+siteId+'/departments').success(
					function(data, status, headers, config) {
						$scope.departments = data;
						$scope.isLoadingDepts = false;
					}).error(function(data, status, headers, config) {
						$scope.isLoadingDepts = false;
						$scope.error = status;
					});
		};

	});
	//------------------------------------------------------------------------------------------------------------------
	// Controller for the navigation bar.. currently has no functions
	//------------------------------------------------------------------------------------------------------------------
	app.controller('NavbarController',
			function($http, $log, $scope, $rootScope) {
				var controller = this;
				$log.debug("Navbar controller...");
		$scope.logOut = function(user) {
			$log.debug("Logging out user...");
			$http.post("/Services/rest/user/logOut", user).success(
					function() {
						$rootScope.loggedIn = false;
						localStorage.removeItem('token');
						localStorage.clear();
						$location.path("/login");
					});
		};
	});

	//------------------------------------------------------------------------------------------------------------------
	// Controller for new blog post view
	//------------------------------------------------------------------------------------------------------------------
	app.controller('BlogController',function($http, $log, $scope, $location) {
				var controller = this;
				$log.debug("Blog controller...");
				$scope.blog={};
				$scope.blog.content = 'Blog text here...';
				$scope.saveBlog = function(blog){
					$http.post("/Services/rest/blogs", blog).success(
							function() {
								$log.debug("Saved blog...");
								$location.path("/");
							});
				};
				$scope.cancel = function(blog){
					$location.path("/");
				};
	});

})($);//Passing jquery object just in case 
