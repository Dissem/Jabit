\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{bfh}[2015/04/21 Atricle Class for my BFH reports]

\DeclareOption{a4paper}{\PassOptionsToClass{\CurrentOption}{scrartcl}}
\DeclareOption{11pt}{\PassOptionsToClass{\CurrentOption}{scrartcl}}
\DeclareOption{oneside}{\PassOptionsToClass{\CurrentOption}{scrartcl}}
\DeclareOption{titlepage}{\PassOptionsToClass{\CurrentOption}{scrartcl}}
\DeclareOption*{\PassOptionsToClass{\CurrentOption}{scrartcl}}
\ExecuteOptions{a4paper,11pt,oneside}
\ProcessOptions

%\RequirePackage{cite}
%\RequirePackage[backend=bibtex]{biblatex}
% Literatur- und Bilderquellen trennen
%\defbibheading{lit}{\section*{Literature}}
%\defbibheading{pic}{\section*{Pictures}}

\LoadClass{scrartcl}
\RequirePackage{remreset}

\RequirePackage[utf8]{inputenc}
\RequirePackage{graphicx}
\RequirePackage{color}
\RequirePackage{lmodern}
\RequirePackage{url}
\RequirePackage{lastpage}

\RequirePackage{mathtools}
\RequirePackage{amsfonts}
%\RequirePackage{float}
\RequirePackage{textgreek}
%\RequirePackage{centernot}
\RequirePackage{hyphenat}

\RequirePackage[T1]{fontenc}
\RequirePackage[scaled]{helvet}

\RequirePackage{textcomp}
\RequirePackage{eurosym}
\RequirePackage{fancyhdr}
\RequirePackage{alltt}
\RequirePackage{verbatim}
\RequirePackage{aeguill}
%\RequirePackage{underscore}
\RequirePackage{ctable}
\RequirePackage[english]{babel}

\RequirePackage{tabularx}
\RequirePackage{wrapfig}
\RequirePackage{ifthen}
\RequirePackage[usenames,dvipsnames,svgnames]{xcolor}
\RequirePackage{hyperref}
\RequirePackage{listings}
\RequirePackage{attachfile}

\RequirePackage{enumitem}
\RequirePackage{wasysym}

\RequirePackage[absolute]{textpos}

\definecolor{bfhblue}{rgb}{0.396,0.49,0.56}  		% Blue
\definecolor{bfhorange}{rgb}{0.961,0.753,0.196}  	% Orange
\definecolor{bfhorangelight}{RGB}{246,216,136}  	% Orange Light

\hypersetup{
	linkcolor=blue,	  % color of internal links
	citecolor=green,	 % color of links to bibliography
	filecolor=blue,	  % color of file links
	urlcolor=blue,	   % color of external links
	colorlinks=true
}
\urlstyle{same}

\typearea{12}
%\bibliographystyle{alpha}
\setcounter{secnumdepth}{4}
\setlength{\parskip}{12pt}
\setlength{\parindent}{0pt}

\renewcommand{\familydefault}{\sfdefault}

%\let\oldtoc\tableofcontents
%\renewcommand{\tableofcontents} {
%	\oldtoc
%	\newpage
%}
\newcommand*{\tutor}[1]{\gdef\@tutor{#1}}
\renewcommand{\maketitle} {
	\begin{titlepage}
        \newlength{\unitlengthtmp}
        \setlength{\unitlengthtmp}{\unitlength}
        \setlength{\unitlength}{1mm}   
        \setlength{\TPHorizModule}{\textwidth}
        \setlength{\TPVertModule}{\textheight} 
        %
        % BFH Logo
        \includegraphics[scale=1.25]{images/logo.pdf}
        %
        % Linien
        \begin{textblock}{1}[0,0](0,0)
	        \begin{picture}(0,130)
	            \put(20,0){\color{bfhblue}\rule{\textwidth}{1.2mm}}
		        \put(20,40){\color{bfhblue}\rule{\textwidth}{1.2mm}}	%28.5	
	        \end{picture}
        \end{textblock} 
        %
        %Zentrierte Titel
        \begin{flushleft}
            \vspace*{4.08cm}
            \textsf{\textbf{\noindent{\Huge{\textcolor{bfhblue}{\@title}}}}}\\[0.4cm]
            \textsf{\huge{\textcolor{bfhblue}{\@subtitle}}}
            %
            %Angaben zum Dokument
            \begin{vfill}
                \begin{tabularx}{\textwidth}{lX}
                \textsf{Author} & \@author\\ 
                \textsf{Tutor}  & \@tutor\\
%                \textsf{Expert} & \textsf\DozentA\\
%                \textsf{Studiengang} & \textsf{\Studiengang}\vspace{5pt}\\
%                \textsf{Autoren} & \textsf\AutorA\\ 
%                               & \textsf\AutorB\vspace{5pt}\\
%                \textsf{Betreuer} & \textsf\DozentA\\
%                                & \textsf\DozentB\vspace{5pt}\\
%                \textsf{Experten} & \textsf\ExpertA\\
%                                  & \textsf\ExpertB\vspace{5pt}\\
                \textsf{Date} & \textsf{\@date}\vspace{5pt}\\
%                &\\
%                &\\
%                \multicolumn{2}{p{\columnwidth-\tabcolsep}}{\textsf{\input{titlepage/titlepage_info}}}\\
            \end{tabularx}
            \end{vfill}
        \end{flushleft}
        \setlength{\unitlength}{\unitlengthtmp}
	\end{titlepage}
}

\pagestyle{fancy}
\fancyhf{}
\fancyfoot[R]{\hrule\thepage/\pageref{LastPage}}
\fancyfoot[L]{\hrule\today}
\fancyhead[L]{\@title}

\fancyhead[R]{
	\includegraphics[height=1.5\baselineskip]{images/logo.png}
	}
\addtolength{\headheight}{2\baselineskip}
\addtolength{\headheight}{0.61pt}

\lstset{
language=XML,	   % Code langugage
basicstyle=\ttfamily\scriptsize,
keywordstyle=\color{OliveGreen},	% Keywords font ('*' = uppercase)
stringstyle=\color{blue},	   % String font
commentstyle=\color{gray},	  % Comments font
numbers=left,	   % Line nums position
numberstyle=\tiny,	  % Line-numbers fonts
stepnumber=1,	   % Step between two line-numbers
numbersep=10pt,	 % How far are line-numbers from code
backgroundcolor=\color{BackgroundBlue}, % Choose background color
frame=none,	 % A frame around the code
tabsize=4,	  % Default tab size
captionpos=b,	   % Caption-position = bottom
breaklines=true,	% Automatic line breaking?
breakatwhitespace=false,	% Automatic breaks only at whitespace?
%showspaces=false,	   % Dont make spaces visible
%showtabs=false,	 % Dont make tabls visible
columns=fixed,	  % Column format
morekeywords={Server, Listener, GlobalNamingResources,
	Resource, ResourceLink, Service, Connector, Engine,
	Host, Context, Environment,
	beans, bean, broker, destinationPolicy, policyMap,
	policyEntries, policyEntry, dispatchPolicy,
	strictOrderDispatchPolicy, subscriptionRecoveryPolicy,
	lastImageSubscriptionRecoveryPolicy, managementContext,
	persistenceAdapter, systemUsage, memoryUsage,
	storeUsage, tempUsage, transportConnectors,
	transportConnector, property, jetty, connectors,
	nioConnector, handlers, webAppContext},
}
% Shows a listing and creates an attachment with the source
\newcommand{\attachlisting}[2][]{
	\hspace{0.95\textwidth}
	\attachfile[icon=Paperclip]{#2}
	\vspace{-5ex}
	\lstinputlisting[#1]{#2}
}
% 1 line number(s)
% 2 variable name
% 3 description
% 4 example values
\newcommand{\listinginfo}[4]{
	\colorbox{WhiteSmoke}{
	\parbox[t]{0.25\textwidth}{
	\printifnotempty{#1}{\texttt{#1:}\\}
	\textit{#2}
	}
	\parbox[t]{0.715\textwidth}{
	\printifnotempty{#3}{#3
	}
	\printifnotempty{#4}{
	\par
	\vspace{1ex}
	\colorbox{BackgroundBlue}{
	\parbox{0.69\textwidth}{
	\vspace{-2ex}
	\ttfamily
	\flushleft{#4}
	}
	}
	\par
	\vspace{0.5ex}
	}
	}
	}
	\par
	\vspace{-1.7ex}
}
\newcommand{\printifnotempty}[2]{
	\ifthenelse{\equal{#1}{}}{}{#2}
}

% Makes a red box that highlights errors or very important warnings
\newcommand{\errorbox}[1]{
	\fcolorbox{Red}{LightPink}{
	\parbox{0.972\textwidth}{
		\begin{wrapfigure}[2]{l}{0.05\textwidth}
			\vspace{-12pt}
			\includegraphics[width=0.05\textwidth]{images/error.pdf}
			\vspace{12pt}
		\end{wrapfigure}
		#1
	}
	}
}

% Makes a yellow box that highlights warnings
\newcommand{\warningbox}[1]{
	\fcolorbox{Goldenrod}{LightYellow}{
	\parbox{0.972\textwidth}{
		\begin{wrapfigure}[2]{l}{0.05\textwidth}
			\vspace{-12pt}
			\includegraphics[width=0.05\textwidth]{images/warning.pdf}
			\vspace{12pt}
		\end{wrapfigure}
		#1
	}
	}
}

% Makes a blue box that highlights special information
\newcommand{\infobox}[1]{
	\fcolorbox{CornflowerBlue}{AliceBlue}{
		\parbox{0.972\textwidth}{
			\begin{wrapfigure}[2]{l}{0.05\textwidth}
				\vspace{-12pt}
				\includegraphics[width=0.05\textwidth]{images/info.pdf}
			\end{wrapfigure}
			#1
		}
	}
}

\RequirePackage{listings}
\definecolor{BackgroundBlue}{cmyk}{0.05,0,0,0}

\let\olditemize=\itemize
\def\itemize{
	\olditemize
	\setlength{\itemsep}{-1.5ex}
}

\newcommand{\leadingzero}[1]{\ifnum #1<10 0\the#1\else\the#1\fi}
%YYYY-MM-DD
\newcommand{\todayI}{\the\year-\leadingzero{\month}-\leadingzero{\day}}

\endinput



